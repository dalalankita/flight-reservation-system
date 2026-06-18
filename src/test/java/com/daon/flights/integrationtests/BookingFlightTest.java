package com.daon.flights.integrationtests;

import com.daon.flights.dto.NewBooking;
import com.daon.flights.dto.NewFlight;
import com.daon.flights.model.Booking;
import com.daon.flights.model.BookingStatus;
import com.daon.flights.model.Flight;
import com.daon.flights.repository.BookingRepository;
import com.daon.flights.repository.FlightRepository;
import com.daon.flights.service.BookingService;
import com.daon.flights.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class BookingFlightTest {

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

    @Test
    void concurrentBookingsNeverOversell() throws InterruptedException {
        int seats = 3;
        int attempts = 20;

        Flight flight = flightService.create(new NewFlight(
                "EI100", "DUB", "LHR", "Dublin",
                LocalDateTime.now().plusDays(10), "Europe/Dublin", seats));
        Long flightId = flight.getId();

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            final int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bookingService.book(flightId, new NewBooking("A" + n, "A" + n + "abc.com"));
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {

                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(succeeded.get()).isEqualTo(seats);

        long taken = bookingRepo.countByFlightIdAndStatusIn(
                flightId, List.of(BookingStatus.HELD, BookingStatus.CONFIRMED));
        assertThat(taken).isEqualTo(seats);
    }

    @Test
    void heldThenConfirmedHoldsSeat_cancelFreesIt() {
        Flight flight = flightService.create(new NewFlight(
                "EI200", "DUB", "CDG", "Dublin",
                LocalDateTime.now().plusDays(5), "Europe/Dublin", 1));

        Booking b = bookingService.book(flight.getId(), new NewBooking("Abc", "abc@gmail.com"));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(flightService.seatsAvailable(flight)).isEqualTo(0);

        bookingService.confirm(b.getId());
        assertThat(flightService.seatsAvailable(flight)).isEqualTo(0);

        bookingService.cancel(b.getId());
        assertThat(flightService.seatsAvailable(flight)).isEqualTo(1);
    }
}
