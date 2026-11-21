package com.campus.lab_resource_platform.maintenance.controller;

import com.campus.lab_resource_platform.maintenance.dto.DowntimeSummaryResponse;
import com.campus.lab_resource_platform.maintenance.dto.EquipmentMaintenanceHistoryResponse;
import com.campus.lab_resource_platform.maintenance.service.DowntimeAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceAnalyticsController {

    private final DowntimeAnalyticsService downtimeAnalyticsService;

    public MaintenanceAnalyticsController(DowntimeAnalyticsService downtimeAnalyticsService) {
        this.downtimeAnalyticsService = downtimeAnalyticsService;
    }

    @GetMapping({"/equipment/{equipmentId}/history", "/history/equipment/{equipmentId}"})
    public ResponseEntity<EquipmentMaintenanceHistoryResponse> getEquipmentMaintenanceHistory(
            @PathVariable Long equipmentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(downtimeAnalyticsService.getEquipmentMaintenanceHistory(equipmentId, authentication.getName()));
    }

    @GetMapping({"/analytics/downtime", "/analytics/downtime-summary"})
    public ResponseEntity<DowntimeSummaryResponse> getDowntimeSummary(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_30_DAYS") String period,
            Authentication authentication
    ) {
        return ResponseEntity.ok(downtimeAnalyticsService.getDowntimeSummary(scope, scopeId, period, authentication.getName()));
    }
}
