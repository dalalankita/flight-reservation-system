package com.ankita.flights.repository;

import com.ankita.flights.model.Flight;

public interface FlightAvailabilityInterface {

    Flight getFlight();

    long getSeatsAvailable();
}