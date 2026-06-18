package com.daon.flights.controller;

import com.daon.flights.dto.BookingResponse;
import com.daon.flights.dto.NewBooking;
import com.daon.flights.model.Booking;
import com.daon.flights.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/flights/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@PathVariable Long id, @Valid @RequestBody NewBooking req) {
        Booking bk = bookings.book(id, req);
        return BookingResponse.of(bk);
    }

    @PostMapping("/bookings/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id) {
        return BookingResponse.of(bookings.confirm(id));
    }

    @DeleteMapping("/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        bookings.cancel(id);
    }
}
