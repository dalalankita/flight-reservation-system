package com.daon.flights.service;

import com.daon.flights.dto.NewFlight;
import com.daon.flights.model.BookingStatus;
import com.daon.flights.model.Flight;
import com.daon.flights.repository.BookingRepository;
import com.daon.flights.repository.FlightRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;
import java.util.Optional;

@Service
public class FlightService {

    private static final List<BookingStatus> TAKEN =
            List.of(BookingStatus.HELD, BookingStatus.CONFIRMED);

    private final FlightRepository flights;
    private final BookingRepository bookings;
    private final Clock clock;

    public FlightService(FlightRepository flights, BookingRepository bookings, Clock clock) {
        this.flights = flights;
        this.bookings = bookings;
        this.clock = clock;
    }

    public Flight get(Long id) {
        Optional<Flight> f = flights.findById(id);
        if (f.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found");
        }
        return f.get();
    }

    public Flight getForUpdate(Long id) {
        return flights.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
    }

    public Flight create(NewFlight req) {
        ZoneId zone;
        try {
            zone = ZoneId.of(req.departureZone());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown departureZone: " + req.departureZone());
        }

        Instant departure = req.scheduledDeparture().atZone(zone).toInstant();
        if (!departure.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Departure must be in the future");
        }

        if (req.origin().equalsIgnoreCase(req.destination())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin and destination must differ");
        }

        Flight f = new Flight();
        f.setFlightNumber(req.flightNumber());
        f.setOrigin(req.origin());
        f.setDestination(req.destination());
        f.setDepartureCity(req.departureCity());
        f.setScheduledDeparture(req.scheduledDeparture());
        f.setDepartureZone(req.departureZone());
        f.setTotalSeats(req.totalSeats());
        return flights.save(f);
    }

    @Transactional
    public void delete(Long id) {
        Flight f = get(id);
        long active = bookings.countByFlightIdAndStatusIn(f.getId(), List.of(BookingStatus.HELD, BookingStatus.CONFIRMED));
        if (active > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flight has active bookings and cannot be removed");
        }

        // cancelled/expired bookings still FK-reference this flight; delete them and
        // flush so the deletes hit the DB before we remove the flight (avoids an FK violation)
        bookings.deleteByFlightId(id);
        bookings.flush();
        flights.delete(f);
    }

    public List<Flight> search(String origin, String destination, LocalDate date) {
        return flights.findAll().stream()
                .filter(f -> origin == null || f.getOrigin().equalsIgnoreCase(origin))
                .filter(f -> destination == null || f.getDestination().equalsIgnoreCase(destination))
                .filter(f -> date == null || f.getScheduledDeparture().toLocalDate().equals(date))
                .filter(f -> seatsAvailable(f) > 0)
                .toList();
    }
    // free seats are derived, not stored: total minus everything currently held or confirmed
    public long seatsAvailable(Flight f) {
        return f.getTotalSeats() - bookings.countByFlightIdAndStatusIn(f.getId(), TAKEN);
    }
}
