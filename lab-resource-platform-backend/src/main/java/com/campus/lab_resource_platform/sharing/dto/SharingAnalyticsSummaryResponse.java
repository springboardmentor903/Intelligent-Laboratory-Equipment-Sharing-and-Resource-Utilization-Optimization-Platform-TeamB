package com.campus.lab_resource_platform.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharingAnalyticsSummaryResponse {

    private Long institutionId;
    private String institutionName;

    // Actual usage hours (based on usage logs, not booked time)
    private BigDecimal totalHoursProvided;
    private BigDecimal totalHoursUsed;
    private BigDecimal totalSharedHours; // Alias for totalHoursProvided

    // Fees
    private BigDecimal feesEarned;
    private BigDecimal totalFeesEarned; // Alias for feesEarned
    private BigDecimal feesOwed;
    private BigDecimal netBalance; // feesEarned - feesOwed
    private BigDecimal expectedFees; // Expected from upcoming/active external bookings

    // Counts & Metrics
    private Long totalBookings;
    private Long totalSharedSessions; // Alias for totalBookings
    private Long noShowCount;
    private BigDecimal noShowRate; // Percentage (e.g. 5.50%)

    private Long activeAgreements;
    private Long activePartnersCount; // Alias for totalPartners
    private Long totalPartners;
    private Long expiringAgreementsCount; // Expiring in next 30 days

    // External share of equipment usage (provided external hours / total equipment usage hours * 100)
    private BigDecimal externalSharePercentage;
}

