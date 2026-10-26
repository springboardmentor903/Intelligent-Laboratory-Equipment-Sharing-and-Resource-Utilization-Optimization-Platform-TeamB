package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.booking.dto.AvailabilitySlotResponse;
import com.campus.lab_resource_platform.booking.service.BookingService;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final BookingService bookingService;
    private final EquipmentService equipmentService;

    public EquipmentController(
            BookingService bookingService,
            EquipmentService equipmentService) {
        this.bookingService = bookingService;
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ResponseEntity<List<Equipment>> getAllEquipment() {
        return ResponseEntity.ok(equipmentService.getAllEquipment());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Equipment> getEquipmentById(
            @PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentById(id));
    }

    @PostMapping
    public ResponseEntity<Equipment> createEquipment(
            @RequestBody Equipment equipment) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.createEquipment(equipment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> updateEquipment(
            @PathVariable Long id,
            @RequestBody Equipment equipment) {
        return ResponseEntity.ok(
                equipmentService.updateEquipment(id, equipment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable Long id) {
        equipmentService.deleteEquipment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<List<AvailabilitySlotResponse>>
    getEquipmentAvailability(
            @PathVariable Long id,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant endDate) {

        if (!startDate.isBefore(endDate)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate must be before endDate");
        }

        List<AvailabilitySlotResponse> slots =
                bookingService.getEquipmentAvailability(
                        id, startDate, endDate);

        return ResponseEntity.ok(slots);
    }
}