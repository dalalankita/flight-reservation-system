package com.ankita.flights.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record NewBooking(
        @NotBlank
        String passengerName,
        @NotBlank @Email
        String passengerEmail
) {}
