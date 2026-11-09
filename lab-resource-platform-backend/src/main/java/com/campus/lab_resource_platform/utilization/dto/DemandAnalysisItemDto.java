package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandAnalysisItemDto {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private String category;
    private String location;

    private Long totalBookings;
    private Long bookedMinutes;
    private Long availableMinutes;

    private Double bookingRate;
    private Double noShowRate;
    private Double cancellationRate;
    private Long waitlistCount;

    private Boolean highDemand;
    private Double thresholdPercent;
}
