package com.campus.lab_resource_platform.maintenance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DowntimeSummaryResponse {

    private String scope;
    private Long scopeId;
    private String period;
    private Long totalRawDowntimeMinutes;
    private Long openHoursDowntimeMinutes;
    private int totalWorkOrdersCount;
    private Double averageDowntimeMinutesPerOrder;
    private Double openHoursLostPercentage;
    private List<EquipmentDowntimeDetailDto> equipmentDowntimes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EquipmentDowntimeDetailDto {
        private Long equipmentId;
        private String equipmentName;
        private String assetTag;
        private Long departmentId;
        private String departmentName;
        private int workOrdersCount;
        private Long rawDowntimeMinutes;
        private Long openHoursDowntimeMinutes;
        private Double percentageOfOpenHoursLost;
        private BigDecimal maintenanceCost;
    }
}
