package com.daon.flights.repository;

import com.daon.flights.model.Booking;
import com.daon.flights.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countByFlightIdAndStatusIn(Long flightId, List<BookingStatus> statuses);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant time);

    void deleteByFlightId(Long flightId);
}
