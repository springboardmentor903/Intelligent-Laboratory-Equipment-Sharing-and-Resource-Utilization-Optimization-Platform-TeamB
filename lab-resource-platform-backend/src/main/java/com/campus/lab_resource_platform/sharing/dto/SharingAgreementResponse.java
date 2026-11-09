package com.campus.lab_resource_platform.sharing.dto;

import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SharingAgreementResponse {

    private Long id;
    private Long accessRequestId;

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;

    private Long ownerInstitutionId;
    private String ownerInstitutionName;

    private Long partnerInstitutionId;
    private String partnerInstitutionName;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime allowedStartTime;
    private LocalTime allowedEndTime;

    private BigDecimal feePerHour;
    private AgreementStatus status;
    private boolean expired;

    private Long approvedByUserId;
    private Instant approvedAt;

    private Long revokedByUserId;
    private Instant revokedAt;
    private String revocationReason;

    private List<AccessGrantResponse> grants;

    private Instant createdAt;
    private Instant updatedAt;
}

