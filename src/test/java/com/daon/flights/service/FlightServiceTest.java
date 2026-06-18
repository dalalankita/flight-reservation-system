package com.daon.flights.service;

import com.daon.flights.dto.NewFlight;
import com.daon.flights.model.Flight;
import com.daon.flights.repository.BookingRepository;
import com.daon.flights.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FlightServiceTest {

    @Mock
    FlightRepository flightRepo;
    @Mock
    BookingRepository bookingRepo;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);

    private FlightService service() {
        return new FlightService(flightRepo, bookingRepo, clock);
    }

    private NewFlight req(String origin, String dest, LocalDateTime sched, String zone) {
        return new NewFlight("EI1", origin, dest, "Dublin", sched, zone, 5);
    }

    private Flight flight(long id, String origin, String dest) {
        Flight f = new Flight();
        f.setId(id); f.setOrigin(origin); f.setDestination(dest); f.setTotalSeats(5);
        return f;
    }

    @Test
    void createRejectsUnknownZone() {
        var ex = assertThrows(ResponseStatusException.class, () ->
                service().create(req("DUB","JFK", LocalDateTime.parse("2030-01-01T10:00:00"), "Mars/Olympus")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsPastDeparture() {
        var ex = assertThrows(ResponseStatusException.class, () ->
                service().create(req("DUB","JFK", LocalDateTime.parse("2000-01-01T10:00:00"), "Europe/Dublin")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsSameOriginAndDestination() {
        var ex = assertThrows(ResponseStatusException.class, () ->
                service().create(req("DUB","DUB", LocalDateTime.parse("2030-01-01T10:00:00"), "Europe/Dublin")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSavesValidFlight() {
        when(flightRepo.save(any(Flight.class))).thenAnswer(inv -> inv.getArgument(0));

        Flight saved = service().create(req("DUB","JFK", LocalDateTime.parse("2030-01-01T10:00:00"), "Europe/Dublin"));

        assertThat(saved.getOrigin()).isEqualTo("DUB");
        assertThat(saved.getTotalSeats()).isEqualTo(5);
    }

    @Test
    void searchFiltersByOrigin() {
        Flight dub = flight(1L, "DUB", "JFK");
        Flight lhr = flight(2L, "LHR", "JFK");

        when(flightRepo.findAll()).thenReturn(List.of(dub, lhr));
        when(bookingRepo.countByFlightIdAndStatusIn(anyLong(), any())).thenReturn(0L);

        assertThat(service().search("DUB", null, null)).containsExactly(dub);
    }

    @Test
    void seatsAvailableSubtractsTakenFromTotal() {
        Flight f = flight(1L, "DUB", "JFK");

        when(bookingRepo.countByFlightIdAndStatusIn(anyLong(), any())).thenReturn(2L);

        assertThat(service().seatsAvailable(f)).isEqualTo(3);
    }

    @Test
    void deleteRejectsWhenActiveBookingsExist() {
        Flight f = flight(1L, "DUB", "LHR");
        when(flightRepo.findById(1L)).thenReturn(Optional.of(f));
        when(bookingRepo.countByFlightIdAndStatusIn(anyLong(), any())).thenReturn(1L);

        var ex = assertThrows(ResponseStatusException.class, () -> service().delete(1L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteRejectsUnknownFlight() {
        when(flightRepo.findById(1L)).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class, () -> service().delete(1L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSucceedsWhenNoActiveBookings() {
        Flight f = flight(1L, "DUB", "LHR");
        when(flightRepo.findById(1L)).thenReturn(Optional.of(f));
        when(bookingRepo.countByFlightIdAndStatusIn(anyLong(), any())).thenReturn(0L);

        assertThatCode(() -> service().delete(1L)).doesNotThrowAnyException();
    }
}
