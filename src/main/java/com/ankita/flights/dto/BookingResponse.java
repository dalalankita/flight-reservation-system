package com.ankita.flights.dto;

import com.ankita.flights.model.Booking;
import com.ankita.flights.model.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long flightId,
        String passengerName,
        BookingStatus status,
        Instant holdExpiresAt
) {
    public static BookingResponse of(Booking b) {
        return new BookingResponse(b.getId(), b.getFlight().getId(),
                b.getPassengerName(), b.getStatus(), b.getHoldExpiresAt());
    }
}
