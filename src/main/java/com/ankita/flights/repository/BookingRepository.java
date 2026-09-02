package com.ankita.flights.repository;

import com.ankita.flights.model.Booking;
import com.ankita.flights.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countByFlightIdAndStatusIn(Long flightId, List<BookingStatus> statuses);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant time);

    void deleteByFlightId(Long flightId);

    @Query("""
        select count(b) from Booking b
        where b.flight.id = :flightId
          and (b.status = com.ankita.flights.model.BookingStatus.CONFIRMED
               or (b.status = com.ankita.flights.model.BookingStatus.HELD
                   and b.holdExpiresAt > :now))
        """)
    long countActiveSeats(@Param("flightId") Long flightId, @Param("now") Instant now);
}
