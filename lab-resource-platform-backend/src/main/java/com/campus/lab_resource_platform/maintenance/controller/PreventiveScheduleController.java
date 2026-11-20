package com.campus.lab_resource_platform.maintenance.controller;

import com.campus.lab_resource_platform.maintenance.dto.PreventiveScheduleDto;
import com.campus.lab_resource_platform.maintenance.dto.PreventiveScheduleResponse;
import com.campus.lab_resource_platform.maintenance.service.PreventiveScheduleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance/schedules")
public class PreventiveScheduleController {

    private final PreventiveScheduleService scheduleService;

    public PreventiveScheduleController(PreventiveScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<PreventiveScheduleResponse> createSchedule(
            @Valid @RequestBody PreventiveScheduleDto dto,
            Authentication authentication
    ) {
        PreventiveScheduleResponse response = scheduleService.createSchedule(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PreventiveScheduleResponse> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody PreventiveScheduleDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scheduleService.updateSchedule(id, dto, authentication.getName()));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<PreventiveScheduleResponse> activateSchedule(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scheduleService.activateSchedule(id, authentication.getName()));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<PreventiveScheduleResponse> deactivateSchedule(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scheduleService.deactivateSchedule(id, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<Page<PreventiveScheduleResponse>> getSchedules(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(scheduleService.getSchedules(equipmentId, activeOnly, authentication.getName(), pageable));
    }
}
