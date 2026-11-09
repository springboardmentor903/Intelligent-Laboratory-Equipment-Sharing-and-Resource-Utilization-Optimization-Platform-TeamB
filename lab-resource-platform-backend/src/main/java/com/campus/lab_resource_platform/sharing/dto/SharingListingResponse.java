package com.campus.lab_resource_platform.sharing.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class SharingListingResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long ownerInstitutionId;
    private String ownerInstitutionName;
    private BigDecimal feePerHour;
    private BigDecimal externalSharePercentage;
    private Integer minNoticeHours;
    private Integer maxBookingDurationHours;
    private String sharingTerms;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}

