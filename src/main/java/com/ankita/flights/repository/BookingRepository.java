package com.ankita.flights.repository;

import com.ankita.flights.model.Booking;
import com.ankita.flights.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countByFlightIdAndStatusIn(Long flightId, List<BookingStatus> statuses);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant time);

    void deleteByFlightId(Long flightId);
}
