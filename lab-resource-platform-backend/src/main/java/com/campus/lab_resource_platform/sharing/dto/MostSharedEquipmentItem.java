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
public class MostSharedEquipmentItem {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private String categoryName;

    private Long providedMinutes;
    private BigDecimal providedHours; // providedMinutes / 60.0

    private Long totalUsageMinutes;
    private BigDecimal totalUsageHours;

    private BigDecimal feesEarned;
    private BigDecimal externalSharePercentage; // (providedMinutes / totalUsageMinutes) * 100

    private Long bookingCount;

    public BigDecimal getSharedHours() {
        return providedHours;
    }

    public Long getSessionsCount() {
        return bookingCount;
    }

    public BigDecimal getRevenue() {
        return feesEarned;
    }

    public String getDemandLevel() {
        if (bookingCount != null && bookingCount >= 3) {
            return "High Demand";
        } else if (bookingCount != null && bookingCount >= 1) {
            return "Optimal Utilization";
        }
        return "Normal";
    }
}

