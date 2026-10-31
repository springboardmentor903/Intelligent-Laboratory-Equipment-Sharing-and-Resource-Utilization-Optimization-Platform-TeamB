package com.campus.lab_resource_platform.equipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "equipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String manufacturer;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(length = 150)
    private String location;

    @Column(length = 2000)
    private String specifications;

    @Column(name = "documentation_url", length = 500)
    private String documentationUrl;

    @Column(length = 255)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "college_id")
    private Long collegeId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "calibration_date")
    private Instant calibrationDate;

    @Column(name = "certification_info", length = 500)
    private String certificationInfo;

    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = true;

    @Column(name = "max_booking_hours", nullable = false)
    @Builder.Default
    private int maxBookingHours = 8;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
