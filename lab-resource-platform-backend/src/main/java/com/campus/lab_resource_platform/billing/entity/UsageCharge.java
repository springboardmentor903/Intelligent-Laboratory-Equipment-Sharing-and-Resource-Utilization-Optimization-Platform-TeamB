package com.campus.lab_resource_platform.billing.entity;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "usage_charges",
        indexes = {
                @Index(name = "idx_charge_booking", columnList = "booking_id", unique = true),
                @Index(name = "idx_charge_user", columnList = "user_id"),
                @Index(name = "idx_charge_equip", columnList = "equipment_id"),
                @Index(name = "idx_charge_inst", columnList = "institution_id"),
                @Index(name = "idx_charge_dept", columnList = "department_id"),
                @Index(name = "idx_charge_type", columnList = "charge_type"),
                @Index(name = "idx_charge_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "department_id")
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 30)
    private ChargeType chargeType;

    @Column(name = "hourly_rate_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRateSnapshot;

    @Column(name = "used_minutes", nullable = false)
    private Integer usedMinutes;

    @Column(name = "total_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCharge;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
