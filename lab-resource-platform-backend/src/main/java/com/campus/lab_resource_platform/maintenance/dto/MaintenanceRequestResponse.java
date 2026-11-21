package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequest;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequestStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequestResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long requesterId;
    private String requesterName;
    private String requesterEmail;
    private String title;
    private String description;
    private MaintenancePriority priority;
    private MaintenanceRequestStatus status;
    private String remarks;
    private Long reviewedById;
    private String reviewedByName;
    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static MaintenanceRequestResponse fromEntity(MaintenanceRequest r) {
        if (r == null) return null;
        return MaintenanceRequestResponse.builder()
                .id(r.getId())
                .equipmentId(r.getEquipment() != null ? r.getEquipment().getId() : null)
                .equipmentName(r.getEquipment() != null ? r.getEquipment().getName() : null)
                .assetTag(r.getEquipment() != null ? r.getEquipment().getAssetTag() : null)
                .requesterId(r.getRequester() != null ? r.getRequester().getId() : null)
                .requesterName(r.getRequester() != null ? r.getRequester().getFullName() : null)
                .requesterEmail(r.getRequester() != null ? r.getRequester().getEmail() : null)
                .title(r.getTitle())
                .description(r.getDescription())
                .priority(r.getPriority())
                .status(r.getStatus())
                .remarks(r.getRemarks())
                .reviewedById(r.getReviewedBy() != null ? r.getReviewedBy().getId() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
