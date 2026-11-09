package com.campus.lab_resource_platform.sharing.controller;

import com.campus.lab_resource_platform.sharing.dto.*;
import com.campus.lab_resource_platform.sharing.service.SharingAnalyticsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sharing/analytics")
public class SharingAnalyticsController {

    private final SharingAnalyticsService analyticsService;

    public SharingAnalyticsController(SharingAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Sharing Analytics Summary
     */
    @GetMapping("/summary")
    public SharingAnalyticsSummaryResponse getSummary(
            @RequestParam(required = false) Long institutionId) {
        return analyticsService.getSummary(institutionId);
    }

    /**
     * Sharing Trends (weekly and monthly)
     */
    @GetMapping("/trends")
    public SharingTrendResponse getTrends(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(defaultValue = "MONTHLY") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return analyticsService.getTrends(institutionId, period, startDate, endDate);
    }

    /**
     * Partner Analytics
     */
    @GetMapping("/partners")
    public Page<PartnerAnalyticsItem> getPartnerAnalytics(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "balance") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = PageRequest.of(page, size);
        return analyticsService.getPartnerAnalytics(institutionId, pageable, sortBy, direction);
    }

    /**
     * Partnership Report (via query params)
     */
    @GetMapping("/partnership-report")
    public PartnershipReportResponse getPartnershipReportQuery(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long partnerInstitutionId,
            @RequestParam(required = false) Long partnerId) {
        Long effectivePartnerId = partnerInstitutionId != null ? partnerInstitutionId : partnerId;
        return analyticsService.getPartnershipReport(institutionId, effectivePartnerId);
    }

    /**
     * Partnership Report (via path variable)
     */
    @GetMapping("/partnerships/{partnerInstitutionId}")
    public PartnershipReportResponse getPartnershipReportPath(
            @PathVariable Long partnerInstitutionId,
            @RequestParam(required = false) Long institutionId) {
        return analyticsService.getPartnershipReport(institutionId, partnerInstitutionId);
    }

    /**
     * Most Shared Equipment
     */
    @GetMapping("/most-shared-equipment")
    public Page<MostSharedEquipmentItem> getMostSharedEquipment(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "providedMinutes") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = PageRequest.of(page, size);
        return analyticsService.getMostSharedEquipment(institutionId, pageable, sortBy, direction);
    }
}

