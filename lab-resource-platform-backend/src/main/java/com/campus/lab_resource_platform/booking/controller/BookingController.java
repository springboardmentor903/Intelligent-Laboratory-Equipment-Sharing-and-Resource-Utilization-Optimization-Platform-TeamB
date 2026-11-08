package com.campus.lab_resource_platform.booking.controller;

import com.campus.lab_resource_platform.booking.dto.BookingRequest;
import com.campus.lab_resource_platform.booking.dto.BookingResponse;
import com.campus.lab_resource_platform.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(@PathVariable Long id) {
        return bookingService.getBookingById(id);
    }

    @GetMapping("/my")
    public Page<BookingResponse> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        return bookingService.getMyBookings(pageable);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return bookingService.cancelBooking(id, reason);
    }
}

