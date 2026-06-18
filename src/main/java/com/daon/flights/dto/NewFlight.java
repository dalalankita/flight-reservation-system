package com.daon.flights.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record NewFlight(
        @NotBlank
        String flightNumber,
        @NotBlank
        String origin,
        @NotBlank
        String destination,
        String departureCity,
        @NotNull
        LocalDateTime scheduledDeparture,
        @NotBlank
        String departureZone,
        @Min(1)
        int totalSeats
) {}
