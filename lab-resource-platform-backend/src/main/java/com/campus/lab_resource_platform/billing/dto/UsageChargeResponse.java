package com.campus.lab_resource_platform.billing.dto;

import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageChargeResponse {

    private Long id;
    private Long bookingId;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long institutionId;
    private Long departmentId;
    private String departmentName;
    private ChargeType chargeType;
    private BigDecimal hourlyRateSnapshot;
    private Integer usedMinutes;
    private BigDecimal totalCharge;
    private Instant createdAt;

    public static UsageChargeResponse fromEntity(UsageCharge c) {
        if (c == null) return null;
        return UsageChargeResponse.builder()
                .id(c.getId())
                .bookingId(c.getBooking() != null ? c.getBooking().getId() : null)
                .equipmentId(c.getEquipment() != null ? c.getEquipment().getId() : null)
                .equipmentName(c.getEquipment() != null ? c.getEquipment().getName() : null)
                .assetTag(c.getEquipment() != null ? c.getEquipment().getAssetTag() : null)
                .userId(c.getUser() != null ? c.getUser().getId() : null)
                .userName(c.getUser() != null ? c.getUser().getFullName() : null)
                .userEmail(c.getUser() != null ? c.getUser().getEmail() : null)
                .institutionId(c.getInstitutionId())
                .departmentId(c.getDepartmentId())
                .chargeType(c.getChargeType())
                .hourlyRateSnapshot(c.getHourlyRateSnapshot())
                .usedMinutes(c.getUsedMinutes())
                .totalCharge(c.getTotalCharge())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
