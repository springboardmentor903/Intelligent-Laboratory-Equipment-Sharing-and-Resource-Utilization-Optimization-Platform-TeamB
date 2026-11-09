package com.campus.lab_resource_platform.sharing.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "sharing_listings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sharing_listing_equipment",
                        columnNames = "equipment_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SharingListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id", nullable = false, unique = true)
    private Equipment equipment;

    @Column(name = "owner_institution_id", nullable = false)
    private Long ownerInstitutionId;

    @Column(name = "fee_per_hour", nullable = false, precision = 12, scale = 2)
    private BigDecimal feePerHour;

    @Column(name = "external_share_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal externalSharePercentage;

    @Column(name = "min_notice_hours", nullable = false)
    private Integer minNoticeHours = 0;

    @Column(name = "max_booking_duration_hours", nullable = false)
    private Integer maxBookingDurationHours;

    @Column(name = "sharing_terms", length = 3000)
    private String sharingTerms;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (minNoticeHours == null) {
            minNoticeHours = 0;
        }
        if (externalSharePercentage == null) {
            externalSharePercentage = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

