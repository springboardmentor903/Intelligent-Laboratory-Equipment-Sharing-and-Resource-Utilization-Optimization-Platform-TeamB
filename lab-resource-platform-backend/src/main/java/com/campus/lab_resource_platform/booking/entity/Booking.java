package com.campus.lab_resource_platform.booking.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_booking_equipment_time", columnList = "equipment_id, start_time, end_time"),
                @Index(name = "idx_booking_user", columnList = "user_id"),
                @Index(name = "idx_booking_agreement", columnList = "sharing_agreement_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.PENDING_APPROVAL;

    @Column(name = "is_external", nullable = false)
    private boolean isExternal = false;

    @Column(name = "sharing_agreement_id")
    private Long sharingAgreementId;

    @Column(name = "fee_per_hour", precision = 12, scale = 2)
    private BigDecimal feePerHour;

    @Column(name = "estimated_fee", precision = 12, scale = 2)
    private BigDecimal estimatedFee;

    @Column(name = "actual_fee", precision = 12, scale = 2)
    private BigDecimal actualFee;

    @Column(length = 1000)
    private String purpose;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

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
            status = BookingStatus.PENDING_APPROVAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
