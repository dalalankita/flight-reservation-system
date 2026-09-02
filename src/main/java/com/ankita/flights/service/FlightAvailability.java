package com.ankita.flights.service;

import com.ankita.flights.model.Flight;

public record FlightAvailability(Flight flight, long seatsAvailable) {}
