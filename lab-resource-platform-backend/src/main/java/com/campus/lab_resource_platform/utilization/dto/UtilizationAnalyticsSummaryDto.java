package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationAnalyticsSummaryDto {

    private String scope; // EQUIPMENT, DEPARTMENT, INSTITUTION
    private Long scopeId;
    private String scopeName;
    private String period; // TODAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM
    private LocalDate startDate;
    private LocalDate endDate;
    private String platformTimeZone;

    private Long totalAvailableMinutes;
    private Double totalAvailableHours;
    private Long totalUsedMinutes;
    private Double totalUsedHours;

    private Double utilizationRate; // Null if totalAvailableMinutes == 0
    private Long sharedUsageMinutes;
    private Double sharedUsagePercent;

    private Double targetUtilizationPercent;
    private Double targetGap; // utilizationRate - targetUtilizationPercent
    private String targetStatus; // ON_TRACK, NEEDS_ATTENTION, NOT_SET

    private Integer equipmentCount;
}
