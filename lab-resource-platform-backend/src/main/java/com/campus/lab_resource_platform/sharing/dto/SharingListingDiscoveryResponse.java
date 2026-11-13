package com.campus.lab_resource_platform.sharing.dto;

import com.campus.lab_resource_platform.sharing.entity.AccessRequestStatus;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
public class SharingListingDiscoveryResponse {

    private Long listingId;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private String description;
    private String specifications;
    private String location;

    private Long categoryId;
    private String categoryName;

    private Long ownerInstitutionId;
    private String ownerInstitutionName;

    // Listing terms
    private BigDecimal feePerHour;
    private BigDecimal externalSharePercentage;
    private Integer minNoticeHours;
    private Integer maxBookingDurationHours;
    private String sharingTerms;
    private boolean active;

    // Logged-in user's request status / active agreement info
    private UserSharingStatus userStatus;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UserSharingStatus {
        private Long latestRequestId;
        private AccessRequestStatus latestRequestStatus;
        private LocalDate requestStartDate;
        private LocalDate requestEndDate;

        private Boolean hasActiveAgreement;
        private Long activeAgreementId;
        private AgreementStatus agreementStatus;
        private LocalDate agreementStartDate;
        private LocalDate agreementEndDate;
        private LocalTime allowedStartTime;
        private LocalTime allowedEndTime;
        private BigDecimal agreementFeePerHour;
    }
}

