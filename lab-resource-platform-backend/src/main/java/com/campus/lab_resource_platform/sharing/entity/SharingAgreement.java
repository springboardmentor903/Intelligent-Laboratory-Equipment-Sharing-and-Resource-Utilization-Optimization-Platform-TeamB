package com.campus.lab_resource_platform.sharing.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "sharing_agreements",
        indexes = {
                @Index(name = "idx_sa_equipment", columnList = "equipment_id"),
                @Index(name = "idx_sa_owner_inst", columnList = "owner_institution_id"),
                @Index(name = "idx_sa_partner_inst", columnList = "partner_institution_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SharingAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_request_id")
    private AccessRequest accessRequest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "owner_institution_id", nullable = false)
    private Long ownerInstitutionId;

    @Column(name = "partner_institution_id", nullable = false)
    private Long partnerInstitutionId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "allowed_start_time")
    private LocalTime allowedStartTime;

    @Column(name = "allowed_end_time")
    private LocalTime allowedEndTime;

    @Column(name = "fee_per_hour", nullable = false, precision = 12, scale = 2)
    private BigDecimal feePerHour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgreementStatus status = AgreementStatus.ACTIVE;

    @Column(name = "approved_by_user_id", nullable = false)
    private Long approvedByUserId;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 1000)
    private String revocationReason;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessGrant> grants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isExpired() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    public boolean isCurrentlyActive() {
        if (status != AgreementStatus.ACTIVE) {
            return false;
        }
        if (isExpired()) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return startDate == null || !today.isBefore(startDate);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (approvedAt == null) {
            approvedAt = now;
        }
        if (status == null) {
            status = AgreementStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

