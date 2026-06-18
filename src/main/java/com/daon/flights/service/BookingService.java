package com.daon.flights.service;

import com.daon.flights.dto.NewBooking;
import com.daon.flights.model.Booking;
import com.daon.flights.model.BookingStatus;
import com.daon.flights.model.Flight;
import com.daon.flights.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;

@Service
public class BookingService {

    private final FlightService flightService;
    private final BookingRepository bookings;
    private final Clock clock;

    @Value("${app.booking-window-minutes}")
    private long windowMinutes;

    @Value("${app.hold-minutes}")
    private long holdMinutes;

    public BookingService(FlightService flightService, BookingRepository bookings, Clock clock) {
        this.flightService = flightService;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional
    public Booking book(Long flightId, NewBooking req) {
        Flight flight = flightService.getForUpdate(flightId);
        Instant now = clock.instant();

        Instant cutoff = flight.departureInstant().minus(Duration.ofMinutes(windowMinutes));
        if (!now.isBefore(cutoff)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bookings closed within " + windowMinutes + " min of departure");
        }

        if (flightService.seatsAvailable(flight) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flight is full");
        }

        Booking b = new Booking();
        b.setFlight(flight);
        b.setPassengerName(req.passengerName());
        b.setPassengerEmail(req.passengerEmail());
        b.setStatus(BookingStatus.HELD);
        b.setCreatedAt(now);
        b.setHoldExpiresAt(now.plus(Duration.ofMinutes(holdMinutes)));
        return bookings.save(b);
    }

    @Transactional
    public Booking confirm(Long bookingId) {
        Booking b = get(bookingId);
        if (b.getStatus() == BookingStatus.CONFIRMED) {
            return b;
        }

        if (b.getStatus() != BookingStatus.HELD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can't confirm a " + b.getStatus() + " booking");
        }

        if (b.getHoldExpiresAt() != null && !clock.instant().isBefore(b.getHoldExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hold has expired");
        }

        b.setStatus(BookingStatus.CONFIRMED);
        b.setHoldExpiresAt(null);
        return b;
    }

    @Transactional
    public void cancel(Long bookingId) {
        Booking b = get(bookingId);
        if (b.getStatus() == BookingStatus.CANCELLED){
            return;
        }
        if (b.getStatus() == BookingStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking already expired");
        }
        b.setStatus(BookingStatus.CANCELLED);
        b.setHoldExpiresAt(null);
    }

    // frees seats whose holds were never confirmed
    @Scheduled(fixedDelayString = "${app.sweep-ms}")
    @Transactional
    public void releaseExpiredHolds() {
        bookings.findByStatusAndHoldExpiresAtBefore(BookingStatus.HELD, clock.instant())
                .forEach(b -> {
                    b.setStatus(BookingStatus.EXPIRED);
                    b.setHoldExpiresAt(null);
                });
    }

    private Booking get(Long id) {
        return bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }
}
