package com.daon.flights.service;

import com.daon.flights.dto.NewBooking;
import com.daon.flights.model.Booking;
import com.daon.flights.model.BookingStatus;
import com.daon.flights.model.Flight;
import com.daon.flights.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    FlightService flightService;
    @Mock
    BookingRepository bookings;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);
    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(flightService, bookings, clock);
        ReflectionTestUtils.setField(service, "windowMinutes", 45L);
        ReflectionTestUtils.setField(service, "holdMinutes", 10L);
    }

    private Flight flightDepartingAt(LocalDateTime sched) {
        Flight f = new Flight();
        f.setId(1L); f.setScheduledDeparture(sched); f.setDepartureZone("UTC"); f.setTotalSeats(5);
        return f;
    }

    private Booking booking(long id, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id); b.setStatus(status);
        return b;
    }

    @Test
    void bookingRejectsInside45MinWindow() {
        when(flightService.getForUpdate(1L))
                .thenReturn(flightDepartingAt(LocalDateTime.parse("2026-06-17T12:30:00")));

        var ex = assertThrows(ResponseStatusException.class, () ->
                service.book(1L, new NewBooking("A","abc@gmail.com")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void bookingRejectsWhenFlightFull() {
        Flight f = flightDepartingAt(LocalDateTime.parse("2026-06-18T12:00:00"));

        when(flightService.getForUpdate(1L)).thenReturn(f);
        when(flightService.seatsAvailable(f)).thenReturn(0L);

        var ex = assertThrows(ResponseStatusException.class,
                () -> service.book(1L, new NewBooking("A","abc@gmail.com")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void bookCreatesHeldBookingWithExpiry() {
        Flight f = flightDepartingAt(LocalDateTime.parse("2026-06-18T12:00:00"));
        when(flightService.getForUpdate(1L)).thenReturn(f);
        when(flightService.seatsAvailable(f)).thenReturn(3L);
        when(bookings.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking b = service.book(1L, new NewBooking("Abc","abc@gmail.com"));

        assertThat(b.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(b.getHoldExpiresAt()).isEqualTo(Instant.parse("2026-06-17T12:10:00Z")); // now + 10 min
    }

    @Test
    void confirmTransitionsHeldToConfirmed() {
        Booking held = booking(7L, BookingStatus.HELD);

        when(bookings.findById(7L)).thenReturn(Optional.of(held));

        Booking result = service.confirm(7L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getHoldExpiresAt()).isNull();
    }

    @Test
    void confirmRejectsCancelledBooking() {
        when(bookings.findById(7L)).thenReturn(Optional.of(booking(7L, BookingStatus.CANCELLED)));

        var ex = assertThrows(ResponseStatusException.class, () -> service.confirm(7L));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelSetsStatusCancelled() {
        Booking held = booking(7L, BookingStatus.HELD);

        when(bookings.findById(7L)).thenReturn(Optional.of(held));

        service.cancel(7L);

        assertThat(held.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void releaseExpiredHoldsMarksThemExpired() {
        Booking stale = booking(7L, BookingStatus.HELD);

        when(bookings.findByStatusAndHoldExpiresAtBefore(any(), any())).thenReturn(List.of(stale));

        service.releaseExpiredHolds();

        assertThat(stale.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }
}
