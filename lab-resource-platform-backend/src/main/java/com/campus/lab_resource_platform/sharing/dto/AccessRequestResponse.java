package com.campus.lab_resource_platform.sharing.dto;

import com.campus.lab_resource_platform.sharing.entity.AccessRequestStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AccessRequestResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;

    private Long requesterId;
    private String requesterName;
    private String requesterEmail;

    private Long requestingInstitutionId;
    private String requestingInstitutionName;

    private Long ownerInstitutionId;
    private String ownerInstitutionName;

    private Long departmentId;
    private String purpose;
    private LocalDate startDate;
    private LocalDate endDate;
    private AccessRequestStatus status;

    // First reviewer's details
    private Long requesterReviewedBy;
    private String requesterReviewedByName;
    private Instant requesterReviewedAt;
    private String requesterReviewRemarks;

    // Final reviewer's details
    private Long finalReviewedBy;
    private String finalReviewedByName;
    private Instant finalReviewedAt;
    private String finalReviewRemarks;

    private Instant createdAt;
    private Instant updatedAt;
}

