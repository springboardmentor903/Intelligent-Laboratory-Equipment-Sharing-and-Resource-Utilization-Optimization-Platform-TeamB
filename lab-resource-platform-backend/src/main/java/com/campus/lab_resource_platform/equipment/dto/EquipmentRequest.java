
package com.campus.lab_resource_platform.equipment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class EquipmentRequest {

    @NotBlank(message = "Equipment name is required")
    @Size(max = 150, message = "Equipment name cannot exceed 150 characters")
    private String name;

    @NotBlank(message = "Asset tag is required")
    @Size(max = 100, message = "Asset tag cannot exceed 100 characters")
    private String assetTag;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @DecimalMin(value = "0.0", message = "Purchase cost cannot be negative")
    private BigDecimal purchaseCost;

    @DecimalMin(value = "0.0", message = "Hourly cost cannot be negative")
    private BigDecimal hourlyCost;

    @PastOrPresent(message = "Purchase date cannot be in the future")
    private LocalDate purchaseDate;

    private Long categoryId;

    private Long departmentId;

    @NotNull(message = "Institution ID is required")
    private Long institutionId;
}
