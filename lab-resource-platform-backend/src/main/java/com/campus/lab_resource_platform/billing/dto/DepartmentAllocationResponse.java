package com.campus.lab_resource_platform.billing.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentAllocationResponse {

    private String period;
    private Long institutionId;
    private BigDecimal grandTotalInternalCost;
    private BigDecimal grandTotalExternalCost;
    private BigDecimal grandTotalCost;
    private List<DepartmentAllocationItemDto> departments;
    private DepartmentAllocationItemDto unallocated;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DepartmentAllocationItemDto {
        private Long departmentId;
        private String departmentName;
        private BigDecimal internalCost;
        private BigDecimal externalCost;
        private BigDecimal totalCost;
        private Double percentageShare;
        private int sessionsCount;
    }
}
