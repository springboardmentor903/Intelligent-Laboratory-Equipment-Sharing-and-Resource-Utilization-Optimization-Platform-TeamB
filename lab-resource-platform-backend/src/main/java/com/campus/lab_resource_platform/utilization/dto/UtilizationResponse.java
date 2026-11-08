package com.campus.lab_resource_platform.utilization.dto;

import com.campus.lab_resource_platform.utilization.entity.UsageType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UtilizationResponse {

    private Long id;
    private Long bookingId;
    private Long equipmentId;
    private String equipmentName;
    private Long userId;
    private String userName;
    private UsageType usageType;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private Integer usedMinutes;
    private BigDecimal actualFee;
    private String notes;
}
