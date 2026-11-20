package com.campus.lab_resource_platform.billing.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentCostAnalyticsResponse {

    private String period;
    private Long institutionId;
    private Double grandTotalUsageHours;
    private BigDecimal grandTotalInternalCost;
    private BigDecimal grandTotalExternalCost;
    private BigDecimal grandTotalUnassignedCost;
    private BigDecimal grandTotalUsageCost;
    private BigDecimal grandTotalMaintenanceCost;
    private List<EquipmentCostItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EquipmentCostItemDto {
        private Long equipmentId;
        private String equipmentName;
        private String assetTag;
        private Long departmentId;
        private String departmentName;
        private Double usageHours;
        private BigDecimal internalCost;
        private BigDecimal externalCost;
        private BigDecimal unassignedCost;
        private BigDecimal totalUsageCost;
        private BigDecimal maintenanceCost;
    }
}
