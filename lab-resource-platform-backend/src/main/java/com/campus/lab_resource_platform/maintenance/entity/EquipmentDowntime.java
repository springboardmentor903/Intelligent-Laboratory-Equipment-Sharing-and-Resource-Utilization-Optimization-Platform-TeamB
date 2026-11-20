package com.campus.lab_resource_platform.maintenance.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "equipment_downtime",
        indexes = {
                @Index(name = "idx_dt_equip", columnList = "equipment_id"),
                @Index(name = "idx_dt_wo", columnList = "work_order_id"),
                @Index(name = "idx_dt_start", columnList = "start_time"),
                @Index(name = "idx_dt_end", columnList = "end_time")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentDowntime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "total_downtime_minutes")
    private Long totalDowntimeMinutes;

    @Column(name = "open_hours_downtime_minutes")
    private Long openHoursDowntimeMinutes;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (startTime == null) {
            startTime = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
