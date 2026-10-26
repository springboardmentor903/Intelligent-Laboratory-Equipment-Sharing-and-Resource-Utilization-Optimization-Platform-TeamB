package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.booking.dto.AvailabilitySlotResponse;
import com.campus.lab_resource_platform.booking.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final BookingService bookingService;

    public EquipmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<List<AvailabilitySlotResponse>> getEquipmentAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        List<AvailabilitySlotResponse> slots = bookingService.getEquipmentAvailability(id, startDate, endDate);
        return ResponseEntity.ok(slots);
    }
}
