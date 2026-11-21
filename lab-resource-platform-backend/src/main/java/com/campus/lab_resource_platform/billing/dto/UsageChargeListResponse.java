package com.campus.lab_resource_platform.billing.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageChargeListResponse {

    private long totalElements;
    private int totalPages;
    private int currentPage;
    private BigDecimal totalAmount;
    private BigDecimal totalInternalAmount;
    private BigDecimal totalExternalAmount;
    private BigDecimal totalUnassignedAmount;
    private List<UsageChargeResponse> charges;
}
