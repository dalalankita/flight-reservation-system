package com.daon.flights.dto;

import com.daon.flights.model.Flight;

import java.time.Instant;
import java.time.LocalDateTime;

public record FlightResponse(
        Long id,
        String flightNumber,
        String origin,
        String destination,
        LocalDateTime scheduledDeparture,
        String departureZone,
        Instant departureInstant,
        int totalSeats,
        long seatsAvailable
) {
    public static FlightResponse of(Flight f, long seatsAvailable) {
        return new FlightResponse(f.getId(), f.getFlightNumber(), f.getOrigin(),
                f.getDestination(), f.getScheduledDeparture(), f.getDepartureZone(),
                f.departureInstant(), f.getTotalSeats(), seatsAvailable);
    }
}
