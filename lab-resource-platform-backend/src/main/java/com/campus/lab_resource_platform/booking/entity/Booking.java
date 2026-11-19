package com.campus.lab_resource_platform.booking.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_booking_equipment_time",
                        columnList = "equipment_id, start_time, end_time"),
                @Index(name = "idx_booking_user", columnList = "user_id"),
                @Index(name = "idx_booking_sharing_agreement",
                        columnList = "sharing_agreement_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(length = 1000)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    // T5: Inter-institution sharing and external booking
    @Column(name = "is_external", columnDefinition = "boolean default false")
    @Builder.Default
    private boolean external = false;

    @Column(name = "sharing_agreement_id")
    private Long sharingAgreementId;

    @Column(name = "fee_per_hour", precision = 12, scale = 2)
    private BigDecimal feePerHour;

    @Column(name = "estimated_fee", precision = 12, scale = 2)
    private BigDecimal estimatedFee;

    @Column(name = "actual_fee", precision = 12, scale = 2)
    private BigDecimal actualFee;

    // Origin: recurring booking support
    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;

    @Column(name = "recurrence_group_id", length = 64)
    private String recurrenceGroupId;

    @Column(name = "recurrence_type", length = 32)
    private String recurrenceType;

    @Version
    @Column(name = "version", columnDefinition = "bigint default 0")
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime == null ? null : startTime.atZone(IST_ZONE).toInstant();
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime == null ? null : endTime.atZone(IST_ZONE).toInstant();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = BookingStatus.PENDING_APPROVAL;
        }
        if (isRecurring == null) {
            isRecurring = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}