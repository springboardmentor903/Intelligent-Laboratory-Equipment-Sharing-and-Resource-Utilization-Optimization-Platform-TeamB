
package com.campus.lab_resource_platform.equipment.dto;

import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EquipmentResponse {

    private Long id;
    private String name;
    private String assetTag;
    private String description;
    private String category;
    private String model;
    private String manufacturer;
    private String serialNumber;
    private String location;
    private String specifications;
    private String documentationUrl;
    private String legacyTags;
    private EquipmentStatus status;
    private BigDecimal purchaseCost;
    private BigDecimal hourlyCost;
    private LocalDate purchaseDate;

    private Long categoryId;
    private String categoryName;

    private Long departmentId;
    private Long institutionId;
    private Long collegeId;

    private Boolean isShareable;
    private Boolean requiresApproval;
    private LocalTime operatingStartTime;
    private LocalTime operatingEndTime;
    private Instant calibrationDate;
    private String certificationInfo;
    private Integer maxBookingHours;
    private Boolean active;

    private List<TagSummary> tags;

    private LocalDate lastCalibrationDate;
    private LocalDate nextCalibrationDate;
    private String calibrationState;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TagSummary {
        private Long id;
        private String name;

        public TagSummary(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
