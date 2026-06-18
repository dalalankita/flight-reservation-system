package com.daon.flights.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Setter
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String flightNumber;

    private String origin;

    private String destination;

    private String departureCity;

    private LocalDateTime scheduledDeparture;

    private String departureZone;

    private int totalSeats;

    public Instant departureInstant() {
        return scheduledDeparture.atZone(ZoneId.of(departureZone)).toInstant();
    }
}
