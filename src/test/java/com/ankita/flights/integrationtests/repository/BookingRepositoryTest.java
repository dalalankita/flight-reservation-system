package com.ankita.flights.integrationtests.repository;

import com.ankita.flights.model.Booking;
import com.ankita.flights.model.BookingStatus;
import com.ankita.flights.model.Flight;
import com.ankita.flights.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
public class BookingRepositoryTest {
    @Autowired
    TestEntityManager em;
    @Autowired
    BookingRepository bookings;

    private final Instant now = Instant.parse("2026-06-17T12:00:00Z");

    @Test
    void countActiveSeats_excludesExpiredHolds() {
        Flight f = new Flight();
        f.setFlightNumber("EI1"); f.setOrigin("DUB"); f.setDestination("LHR");
        f.setScheduledDeparture(LocalDateTime.parse("2026-06-27T12:00:00"));
        f.setDepartureZone("Europe/Dublin"); f.setTotalSeats(5);
        em.persist(f);

        em.persist(booking(f, BookingStatus.CONFIRMED, null));
        em.persist(booking(f, BookingStatus.HELD, now.plusSeconds(600))); // live hold
        em.persist(booking(f, BookingStatus.HELD, now.minusSeconds(1)));  // expired hold
        em.persist(booking(f, BookingStatus.CANCELLED, null));
        em.persist(booking(f, BookingStatus.EXPIRED, null));
        em.flush();

        // confirmed + live hold = 2; the expired hold must NOT count
        assertThat(bookings.countActiveSeats(f.getId(), now)).isEqualTo(2);
    }

    private Booking booking(Flight f, BookingStatus status, Instant expires) {
        Booking b = new Booking();
        b.setFlight(f); b.setStatus(status);
        b.setPassengerName("abc"); b.setPassengerEmail("abc@gmail.com");
        b.setCreatedAt(now); b.setHoldExpiresAt(expires);
        return b;
    }
}
