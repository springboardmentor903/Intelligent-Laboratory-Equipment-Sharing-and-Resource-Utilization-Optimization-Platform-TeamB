package com.campus.lab_resource_platform.billing.controller;

import com.campus.lab_resource_platform.billing.dto.UsageChargeListResponse;
import com.campus.lab_resource_platform.billing.dto.UsageChargeResponse;
import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.service.UsageChargeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/billing/charges")
public class UsageChargeController {

    private final UsageChargeService chargeService;

    public UsageChargeController(UsageChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @GetMapping("/my")
    public ResponseEntity<Page<UsageChargeResponse>> getMyCharges(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(chargeService.getMyCharges(authentication.getName(), pageable));
    }

    @GetMapping("/institution")
    public ResponseEntity<UsageChargeListResponse> getInstitutionCharges(
            @RequestParam(required = false) ChargeType chargeType,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(chargeService.getInstitutionCharges(
                chargeType, departmentId, equipmentId, startDate, endDate, authentication.getName(), pageable));
    }
}
