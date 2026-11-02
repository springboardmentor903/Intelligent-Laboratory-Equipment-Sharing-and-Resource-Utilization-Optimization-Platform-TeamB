package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationSummaryDto {

    private Long collegeId;
    private String collegeName;
    private long totalEquipments;
    private long availableCount;
    private long inUseCount;
    private long underMaintenanceCount;
    private long decommissionedCount;
    private double overallUtilizationRatePercent;
    private long totalUsageMinutesToday;
    private long activeSessionsCount;
    private List<EquipmentLiveStatusDto> equipments;
}

