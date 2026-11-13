package com.campus.lab_resource_platform.equipment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

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

    @Column(name = "asset_tag", unique = true, length = 100)
    private String assetTag;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "hourly_cost", precision = 12, scale = 2)
    private BigDecimal hourlyCost;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private EquipmentCategory category;

    @ManyToMany
    @JoinTable(
            name = "equipment_tag_mapping",
            joinColumns = @JoinColumn(name = "equipment_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @JsonIgnore
    @Builder.Default
    private Set<Tag> tagEntities = new HashSet<>();

    @JsonProperty("tags")
    @Column(name = "tags", length = 255)
    private String tags;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonProperty("category")
    @Column(name = "category", length = 100)
    private String legacyCategory;

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

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "college_id")
    private Long collegeId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "is_shareable", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isShareable = false;

    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private Boolean requiresApproval = true;

    @Column(name = "operating_start_time")
    @Builder.Default
    private LocalTime operatingStartTime = LocalTime.of(8, 0);

    @Column(name = "operating_end_time")
    @Builder.Default
    private LocalTime operatingEndTime = LocalTime.of(18, 0);

    @Column(name = "calibration_date")
    private Instant calibrationDate;

    @Column(name = "certification_info", length = 500)
    private String certificationInfo;

    @Column(name = "max_booking_hours", nullable = false)
    @Builder.Default
    private int maxBookingHours = 8;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @JsonProperty("category")
    public String getLegacyCategory() {
        if (legacyCategory != null) {
            return legacyCategory;
        }
        return category != null ? category.getName() : null;
    }

    @JsonProperty("category")
    public void setLegacyCategory(String legacyCategory) {
        this.legacyCategory = legacyCategory;
    }

    public String getLegacyTags() {
        return tags;
    }

    public void setLegacyTags(String legacyTags) {
        this.tags = legacyTags;
    }

    @PrePersist
    @PreUpdate
    protected void normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (assetTag != null) {
            assetTag = assetTag.trim();
        }
        if (status == null) {
            status = EquipmentStatus.AVAILABLE;
        }
        if (isShareable == null) {
            isShareable = false;
        }
        if (requiresApproval == null) {
            requiresApproval = true;
        }
        if (operatingStartTime == null) {
            operatingStartTime = LocalTime.of(8, 0);
        }
        if (operatingEndTime == null) {
            operatingEndTime = LocalTime.of(18, 0);
        }
        if (tagEntities == null) {
            tagEntities = new HashSet<>();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
}
