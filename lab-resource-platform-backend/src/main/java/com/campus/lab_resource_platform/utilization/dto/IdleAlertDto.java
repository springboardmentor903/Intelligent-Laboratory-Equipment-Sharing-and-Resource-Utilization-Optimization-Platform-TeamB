package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdleAlertDto {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private String model;
    private String category;
    private Long departmentId;
    private String departmentName;
    private Long collegeId;
    private String collegeName;

    private Double idlePercentage;
    private Double idleHours;
    private Double availableHours;
    private Double bookedHours;
    private Instant lastBookingEndDate;
}
