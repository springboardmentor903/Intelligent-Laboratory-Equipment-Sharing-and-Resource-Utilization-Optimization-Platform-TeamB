package com.campus.lab_resource_platform.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnershipReportResponse {

    private Long institutionId;
    private String institutionName;
    private Long partnerInstitutionId;
    private String partnerInstitutionName;

    // Both directions of sharing
    private BigDecimal hoursProvided;
    private BigDecimal hoursUsed;
    private BigDecimal totalHours; // hoursProvided + hoursUsed

    private BigDecimal feesEarned;
    private BigDecimal feesOwed;
    private BigDecimal balance; // feesEarned - feesOwed

    private Long totalBookings;
    private Long noShowCount;
    private BigDecimal noShowRate;

    // Monthly trends for this partnership
    @Builder.Default
    private List<SharingTrendItem> monthlyTrends = new ArrayList<>();

    // Agreement details with statuses
    @Builder.Default
    private List<PartnershipAgreementDetail> agreements = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartnershipAgreementDetail {
        private Long agreementId;
        private Long equipmentId;
        private String equipmentName;
        private String assetTag;
        private Long ownerInstitutionId;
        private Long partnerInstitutionId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal feePerHour;
        private String status; // "ACTIVE", "EXPIRED", "REVOKED"
        private LocalTime allowedStartTime;
        private LocalTime allowedEndTime;
    }
}

