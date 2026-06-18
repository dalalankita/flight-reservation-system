package com.daon.flights.integrationtests;

import com.daon.flights.dto.NewBooking;
import com.daon.flights.dto.NewFlight;
import com.daon.flights.model.Booking;
import com.daon.flights.model.Flight;
import com.daon.flights.repository.BookingRepository;
import com.daon.flights.repository.FlightRepository;
import com.daon.flights.service.BookingService;
import com.daon.flights.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class FlightDeleteTest {

    @Autowired
    FlightService flightService;
    @Autowired
    BookingService bookingService;
    @Autowired
    FlightRepository flightRepo;
    @Autowired
    BookingRepository bookingRepo;

    @BeforeEach
    void clean() {
        bookingRepo.deleteAll();
        flightRepo.deleteAll();
    }

    private Flight newFlight(String number, int seats) {
        return flightService.create(new NewFlight(
                number, "DUB", "LHR", "Dublin",
                LocalDateTime.now().plusDays(3), "Europe/Dublin", seats));
    }

    @Test
    void deletesFlightWithOnlyCancelledBooking() {
        Flight f = newFlight("EI1", 2);
        Booking b = bookingService.book(f.getId(), new NewBooking("abc", "abc@gmail.com"));
        bookingService.cancel(b.getId());

        flightService.delete(f.getId());

        assertThat(flightRepo.findById(f.getId())).isEmpty();
        assertThat(bookingRepo.findById(b.getId())).isEmpty();
    }

    @Test
    void rejectsDeleteWhenActiveBookingExists() {
        Flight f = newFlight("EI2", 2);
        bookingService.book(f.getId(), new NewBooking("abc", "abc@gmail.com"));

        var ex = assertThrows(ResponseStatusException.class, () -> flightService.delete(f.getId()));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(flightRepo.findById(f.getId())).isPresent();
    }

}
