package com.campus.lab_resource_platform.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharingTrendItem {

    private String periodLabel; // e.g. "2026-11" or "2026-W45"
    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal hoursProvided;
    private BigDecimal hoursUsed;
    private BigDecimal feesEarned;
    private BigDecimal feesOwed;
    private BigDecimal netBalance; // feesEarned - feesOwed

    public String getPeriod() {
        return periodLabel;
    }

    public BigDecimal getSharedHours() {
        return hoursProvided;
    }
}

