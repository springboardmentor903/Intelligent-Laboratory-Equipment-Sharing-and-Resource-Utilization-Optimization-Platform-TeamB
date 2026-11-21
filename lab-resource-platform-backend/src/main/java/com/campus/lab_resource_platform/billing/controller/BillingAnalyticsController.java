package com.campus.lab_resource_platform.billing.controller;

import com.campus.lab_resource_platform.billing.dto.DepartmentAllocationResponse;
import com.campus.lab_resource_platform.billing.dto.EquipmentCostAnalyticsResponse;
import com.campus.lab_resource_platform.billing.service.BillingAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing/analytics")
public class BillingAnalyticsController {

    private final BillingAnalyticsService billingAnalyticsService;

    public BillingAnalyticsController(BillingAnalyticsService billingAnalyticsService) {
        this.billingAnalyticsService = billingAnalyticsService;
    }

    @GetMapping("/equipment-cost")
    public ResponseEntity<EquipmentCostAnalyticsResponse> getEquipmentCost(
            @RequestParam(defaultValue = "LAST_30_DAYS") String period,
            Authentication authentication
    ) {
        return ResponseEntity.ok(billingAnalyticsService.getEquipmentCostAnalytics(period, authentication.getName()));
    }

    @GetMapping("/department-allocation")
    public ResponseEntity<DepartmentAllocationResponse> getDepartmentAllocation(
            @RequestParam(defaultValue = "LAST_30_DAYS") String period,
            @RequestParam(required = false) Long departmentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(billingAnalyticsService.getDepartmentAllocationAnalytics(period, departmentId, authentication.getName()));
    }
}
