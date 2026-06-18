package com.daon.flights.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class FlightTest {

    private Flight flight(LocalDateTime sched, String zone) {
        Flight f = new Flight();
        f.setScheduledDeparture(sched);
        f.setDepartureZone(zone);
        return f;
    }

    @Test
    void dublinSummerIsUtcPlusOne() {   // IST / DST
        assertThat(flight(LocalDateTime.parse("2026-07-01T14:30:00"), "Europe/Dublin").departureInstant())
                .isEqualTo(Instant.parse("2026-07-01T13:30:00Z"));
    }

    @Test
    void utcZoneIsUnchanged() {
        assertThat(flight(LocalDateTime.parse("2030-03-01T08:00:00"), "UTC").departureInstant())
                .isEqualTo(Instant.parse("2030-03-01T08:00:00Z"));
    }
}
