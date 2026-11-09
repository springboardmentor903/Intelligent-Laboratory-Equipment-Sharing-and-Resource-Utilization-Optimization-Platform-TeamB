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
public class PartnerAnalyticsItem {

    private Long partnerInstitutionId;
    private String partnerInstitutionName;

    private BigDecimal hoursProvided;
    private BigDecimal hoursUsed;
    private BigDecimal feesEarned;
    private BigDecimal feesOwed;
    private BigDecimal balance; // feesEarned - feesOwed

    private Long activeAgreementsCount;
    private Long totalBookings;
}

