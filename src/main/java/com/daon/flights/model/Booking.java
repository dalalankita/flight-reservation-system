package com.daon.flights.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Flight flight;

    private String passengerName;

    private String passengerEmail;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private Instant createdAt;

    private Instant holdExpiresAt;
}
