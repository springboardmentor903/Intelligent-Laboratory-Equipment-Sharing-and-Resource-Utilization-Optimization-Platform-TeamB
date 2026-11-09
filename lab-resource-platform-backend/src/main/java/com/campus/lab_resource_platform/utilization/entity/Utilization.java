package com.campus.lab_resource_platform.utilization.entity;

import com.campus.lab_resource_platform.booking.entity.Booking;
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
        name = "utilizations",
        indexes = {
                @Index(name = "idx_util_booking", columnList = "booking_id"),
                @Index(name = "idx_util_equipment", columnList = "equipment_id"),
                @Index(name = "idx_util_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilization {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 30)
    @Builder.Default
    private UsageType usageType = UsageType.INTERNAL;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Column(name = "used_minutes")
    private Integer usedMinutes;

    @Column(name = "actual_duration_minutes")
    private Long actualDurationMinutes;

    @Column(name = "actual_fee", precision = 12, scale = 2)
    private BigDecimal actualFee;

    @Column(length = 1000)
    private String notes;

    @Column(name = "operator_notes", length = 1000)
    private String operatorNotes;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime == null ? null : checkInTime.atZone(IST_ZONE).toInstant();
    }

    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }

    public void setCheckOutTime(LocalDateTime checkOutTime) {
        this.checkOutTime = checkOutTime == null ? null : checkOutTime.atZone(IST_ZONE).toInstant();
    }

    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (checkInTime == null) {
            checkInTime = now;
        }
        if (usageType == null) {
            usageType = UsageType.INTERNAL;
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
