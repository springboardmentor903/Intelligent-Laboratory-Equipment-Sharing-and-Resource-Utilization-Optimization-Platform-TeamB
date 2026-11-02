package com.campus.lab_resource_platform.utilization.dto;

import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentLiveStatusDto {

    private Long equipmentId;
    private String equipmentName;
    private String model;
    private String category;
    private String location;
    private Long collegeId;
    private String collegeName;
    private EquipmentStatus status; // AVAILABLE, IN_USE, UNDER_MAINTENANCE, DECOMMISSIONED
    private boolean isCurrentlyInUse;
    private Long currentUtilizationId;
    private Long currentBookingId;
    private String currentUserName;
    private String currentUserEmail;
    private Instant sessionStartTime;
    private Long elapsedMinutes;
    private double todayUtilizationRatePercent;
    private double weeklyUtilizationRatePercent;
    private long totalUsageMinutesToday;
    private long totalSessionsCompleted;
}

