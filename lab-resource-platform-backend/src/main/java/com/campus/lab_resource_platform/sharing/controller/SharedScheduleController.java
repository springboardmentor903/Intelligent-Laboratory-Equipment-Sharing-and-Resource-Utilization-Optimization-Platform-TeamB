package com.campus.lab_resource_platform.sharing.controller;

import com.campus.lab_resource_platform.sharing.dto.SharedScheduleResponse;
import com.campus.lab_resource_platform.sharing.service.SharedScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
public class SharedScheduleController {

    private final SharedScheduleService scheduleService;

    public SharedScheduleController(SharedScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping({"/api/equipment/{equipmentId}/shared-schedule", "/api/sharing/equipment/{equipmentId}/shared-schedule"})
    public SharedScheduleResponse getSharedSchedule(
            @PathVariable Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return scheduleService.getSharedSchedule(equipmentId, startDate, endDate);
    }
}

