package com.campus.lab_resource_platform.sharing.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "access_requests",
        indexes = {
                @Index(name = "idx_ar_equipment_requester", columnList = "equipment_id, requester_id"),
                @Index(name = "idx_ar_req_inst", columnList = "requesting_institution_id"),
                @Index(name = "idx_ar_owner_inst", columnList = "owner_institution_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "requesting_institution_id", nullable = false)
    private Long requestingInstitutionId;

    @Column(name = "owner_institution_id", nullable = false)
    private Long ownerInstitutionId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    // First reviewer's details (Requesting Institution Administrator)
    @Column(name = "requester_reviewed_by")
    private Long requesterReviewedBy;

    @Column(name = "requester_reviewed_by_name", length = 150)
    private String requesterReviewedByName;

    @Column(name = "requester_reviewed_at")
    private Instant requesterReviewedAt;

    @Column(name = "requester_review_remarks", length = 1000)
    private String requesterReviewRemarks;

    // Final reviewer's details (Owner Institution Administrator)
    @Column(name = "final_reviewed_by")
    private Long finalReviewedBy;

    @Column(name = "final_reviewed_by_name", length = 150)
    private String finalReviewedByName;

    @Column(name = "final_reviewed_at")
    private Instant finalReviewedAt;

    @Column(name = "final_review_remarks", length = 1000)
    private String finalReviewRemarks;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = AccessRequestStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

