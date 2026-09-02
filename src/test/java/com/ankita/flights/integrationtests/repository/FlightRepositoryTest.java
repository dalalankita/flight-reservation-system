package com.ankita.flights.integrationtests.repository;

import com.ankita.flights.model.Booking;
import com.ankita.flights.model.BookingStatus;
import com.ankita.flights.model.Flight;
import com.ankita.flights.repository.FlightAvailabilityInterface;
import com.ankita.flights.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
public class FlightRepositoryTest {

    @Autowired
    TestEntityManager em;
    @Autowired
    FlightRepository flights;

    private final Instant now = Instant.parse("2026-06-17T12:00:00Z");

    @Test
    void searchAvailable_filtersByOrigin() {
        persistFlight("EI1", "DUB", "JFK", 5);
        persistFlight("EI2", "LHR", "JFK", 5);
        em.flush();

        List<FlightAvailabilityInterface> results = flights.searchAvailable("DUB", null, now);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFlight().getOrigin()).isEqualTo("DUB");
    }

    @Test
    void searchAvailable_filtersByDestination() {
        persistFlight("EI1", "DUB", "JFK", 5);
        persistFlight("EI2", "DUB", "LHR", 5);
        em.flush();

        List<FlightAvailabilityInterface> results = flights.searchAvailable("DUB", "LHR", now);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFlight().getDestination()).isEqualTo("LHR");
    }

    @Test
    void searchAvailable_matchesCaseInsensitively() {
        persistFlight("EI1", "DUB", "JFK", 5);
        em.flush();

        // query lower()s both sides, so a lowercase param must still match an upper-case column
        assertThat(flights.searchAvailable("dub", null, now)).hasSize(1);
    }

    @Test
    void searchAvailable_nullFiltersReturnAllAvailable() {
        persistFlight("EI1", "DUB", "JFK", 5);
        persistFlight("EI2", "LHR", "CDG", 5);
        em.flush();

        assertThat(flights.searchAvailable(null, null, now)).hasSize(2);
    }

    @Test
    void searchAvailable_excludesFullFlights() {
        Flight open = persistFlight("EI1", "DUB", "JFK", 2);
        Flight full = persistFlight("EI2", "DUB", "LHR", 1);
        // 'full' has its single seat taken by a live hold -> HAVING drops it
        em.persist(booking(full, BookingStatus.HELD, now.plusSeconds(600)));
        em.flush();

        List<FlightAvailabilityInterface> results = flights.searchAvailable("DUB", null, now);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFlight().getFlightNumber()).isEqualTo("EI1");
    }

    @Test
    void searchAvailable_expiredHoldDoesNotConsumeSeat() {
        Flight f = persistFlight("EI1", "DUB", "JFK", 1);
        // an expired hold must NOT count, so the flight stays available with its seat free
        em.persist(booking(f, BookingStatus.HELD, now.minusSeconds(1)));
        em.flush();

        List<FlightAvailabilityInterface> results = flights.searchAvailable("DUB", null, now);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSeatsAvailable()).isEqualTo(1);
    }

    @Test
    void searchAvailable_reportsCorrectSeatCount() {
        Flight f = persistFlight("EI1", "DUB", "JFK", 5);
        em.persist(booking(f, BookingStatus.CONFIRMED, null));            // -1
        em.persist(booking(f, BookingStatus.HELD, now.plusSeconds(600))); // -1 (live)
        em.persist(booking(f, BookingStatus.HELD, now.minusSeconds(1)));  // 0 (expired)
        em.persist(booking(f, BookingStatus.CANCELLED, null));            // 0
        em.persist(booking(f, BookingStatus.EXPIRED, null));             // 0
        em.flush();

        List<FlightAvailabilityInterface> results = flights.searchAvailable("DUB", null, now);

        assertThat(results)
                .extracting(v -> v.getFlight().getFlightNumber(), FlightAvailabilityInterface::getSeatsAvailable)
                .containsExactly(tuple("EI1", 3L)); // 5 - 2 active = 3
    }

    private Flight persistFlight(String number, String origin, String destination, int seats) {
        Flight f = new Flight();
        f.setFlightNumber(number);
        f.setOrigin(origin);
        f.setDestination(destination);
        f.setDepartureCity("Dublin");
        f.setScheduledDeparture(LocalDateTime.parse("2026-06-27T12:00:00"));
        f.setDepartureZone("Europe/Dublin");
        f.setTotalSeats(seats);
        return em.persist(f);
    }

    private Booking booking(Flight f, BookingStatus status, Instant expires) {
        Booking b = new Booking();
        b.setFlight(f);
        b.setStatus(status);
        b.setPassengerName("abc");
        b.setPassengerEmail("abc@gmail.com");
        b.setCreatedAt(now);
        b.setHoldExpiresAt(expires);
        return b;
    }
}
