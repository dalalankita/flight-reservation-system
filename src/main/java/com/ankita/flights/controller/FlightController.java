package com.ankita.flights.controller;

import com.ankita.flights.dto.FlightResponse;
import com.ankita.flights.dto.NewFlight;
import com.ankita.flights.model.Flight;
import com.ankita.flights.service.FlightService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
public class FlightController {

    private final FlightService flights;

    public FlightController(FlightService flights) {
        this.flights = flights;
    }

    @GetMapping("/flights")
    public List<FlightResponse> list(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {


        return flights.search(origin, destination, date).stream()
                .map(fa -> FlightResponse.of(fa.flight(), fa.seatsAvailable()))
                .toList();
    }

    @PostMapping("/admin/flights")
    @ResponseStatus(HttpStatus.CREATED)
    public FlightResponse create(@Valid @RequestBody NewFlight req) {
        Flight f = flights.create(req);
        return FlightResponse.of(f, f.getTotalSeats());
    }

    @DeleteMapping("/admin/flights/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        flights.delete(id);
    }
}
