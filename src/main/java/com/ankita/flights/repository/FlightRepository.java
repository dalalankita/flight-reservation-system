package com.ankita.flights.repository;

import com.ankita.flights.model.Flight;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flight f where f.id = :id")
    Optional<Flight> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select f as flight,
               f.totalSeats - count(b) as seatsAvailable
        from Flight f
        left join Booking b
               on b.flight = f
              and (b.status = com.ankita.flights.model.BookingStatus.CONFIRMED
                   or (b.status = com.ankita.flights.model.BookingStatus.HELD
                       and b.holdExpiresAt > :now))
        where (:origin is null or lower(f.origin) = lower(cast(:origin as string)))
          and (:destination is null or lower(f.destination) = lower(cast(:destination as string)))
        group by f
        having f.totalSeats - count(b) > 0
        """)
    List<FlightAvailabilityInterface> searchAvailable(@Param("origin") String origin,
                                                      @Param("destination") String destination,
                                                      @Param("now") Instant now);

}
