
package com.campus.lab_resource_platform.equipment.dto;

import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EquipmentResponse {

    private Long id;
    private String name;
    private String assetTag;
    private String description;
    private EquipmentStatus status;
    private BigDecimal purchaseCost;
    private BigDecimal hourlyCost;
    private LocalDate purchaseDate;

    private Long categoryId;
    private String categoryName;

    private Long departmentId;
    private Long institutionId;

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
