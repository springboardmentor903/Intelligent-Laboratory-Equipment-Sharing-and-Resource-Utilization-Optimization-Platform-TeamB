package com.campus.lab_resource_platform.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentBudgetRequest {

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @NotNull(message = "Fiscal year is required")
    private Integer fiscalYear;

    @NotNull(message = "Allocated budget amount is required")
    @DecimalMin(value = "0.00", message = "Allocated amount cannot be negative")
    private BigDecimal allocatedAmount;
}
