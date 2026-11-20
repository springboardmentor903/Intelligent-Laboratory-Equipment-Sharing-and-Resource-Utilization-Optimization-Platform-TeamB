package com.campus.lab_resource_platform.billing.dto;

import com.campus.lab_resource_platform.billing.entity.BudgetStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentBudgetResponse {

    private Long id;
    private Long institutionId;
    private Long departmentId;
    private String departmentName;
    private Integer fiscalYear;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private Double utilizationPercentage;
    private BudgetStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
