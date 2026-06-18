package com.daon.flights.dto;

import com.daon.flights.model.Booking;
import com.daon.flights.model.BookingStatus;

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
