package com.campus.lab_resource_platform.utilization.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "utilization_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_equipment_snapshot_date", columnNames = {"equipment_id", "snapshot_date"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "available_minutes", nullable = false)
    private Integer availableMinutes;

    @Column(name = "used_minutes", nullable = false)
    private Integer usedMinutes;

    @Column(name = "shared_minutes", nullable = false)
    @Builder.Default
    private Integer sharedMinutes = 0;

    @Column(name = "utilization_percent")
    private Double utilizationPercent;
}
