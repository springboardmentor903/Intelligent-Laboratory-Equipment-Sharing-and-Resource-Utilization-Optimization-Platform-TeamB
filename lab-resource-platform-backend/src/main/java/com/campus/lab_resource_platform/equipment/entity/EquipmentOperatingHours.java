package com.campus.lab_resource_platform.equipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(
        name = "equipment_operating_hours",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_equipment_day", columnNames = {"equipment_id", "day_of_week"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentOperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 1 = Monday, ..., 7 = Sunday (java.time.DayOfWeek.getValue())

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;
}
