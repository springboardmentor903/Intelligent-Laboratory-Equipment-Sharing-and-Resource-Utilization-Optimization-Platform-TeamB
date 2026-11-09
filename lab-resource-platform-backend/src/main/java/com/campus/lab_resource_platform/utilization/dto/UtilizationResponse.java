package com.campus.lab_resource_platform.utilization.dto;

import com.campus.lab_resource_platform.utilization.entity.UsageType;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationResponse {

    private Long id;
    private Long bookingId;
    private Long equipmentId;
    private String equipmentName;
    private String equipmentModel;
    private String equipmentCategory;
    private Long collegeId;
    private String collegeName;
    private Long userId;
    private String userName;
    private String userEmail;
    private UsageType usageType;
    private Instant checkInTime;
    private Instant checkOutTime;
    private Integer usedMinutes;
    private Long actualDurationMinutes;
    private BigDecimal actualFee;
    private String notes;
    private String operatorNotes;
    private String status;
    private Instant createdAt;

    public static UtilizationResponse fromEntity(Utilization utilization, String collegeName) {
        if (utilization == null) {
            return null;
        }
        Long collegeId = null;
        String category = null;
        if (utilization.getEquipment() != null) {
            collegeId = utilization.getEquipment().getCollegeId() != null
                    ? utilization.getEquipment().getCollegeId()
                    : utilization.getEquipment().getInstitutionId();
            category = utilization.getEquipment().getCategory() != null
                    ? utilization.getEquipment().getCategory().getName()
                    : utilization.getEquipment().getLegacyCategory();
        }
        Long duration = utilization.getActualDurationMinutes();
        if (duration == null && utilization.getUsedMinutes() != null) {
            duration = utilization.getUsedMinutes().longValue();
        }
        Integer usedMinutes = utilization.getUsedMinutes();
        if (usedMinutes == null && duration != null) {
            usedMinutes = Math.toIntExact(Math.min(duration, Integer.MAX_VALUE));
        }
        return UtilizationResponse.builder()
                .id(utilization.getId())
                .bookingId(utilization.getBooking() != null ? utilization.getBooking().getId() : null)
                .equipmentId(utilization.getEquipment() != null ? utilization.getEquipment().getId() : null)
                .equipmentName(utilization.getEquipment() != null ? utilization.getEquipment().getName() : null)
                .equipmentModel(utilization.getEquipment() != null ? utilization.getEquipment().getModel() : null)
                .equipmentCategory(category)
                .collegeId(collegeId)
                .collegeName(collegeName)
                .userId(utilization.getUser() != null ? utilization.getUser().getId() : null)
                .userName(utilization.getUser() != null ? utilization.getUser().getFullName() : null)
                .userEmail(utilization.getUser() != null ? utilization.getUser().getEmail() : null)
                .usageType(utilization.getUsageType())
                .checkInTime(utilization.getCheckInTime())
                .checkOutTime(utilization.getCheckOutTime())
                .usedMinutes(usedMinutes)
                .actualDurationMinutes(duration)
                .actualFee(utilization.getActualFee())
                .notes(utilization.getNotes())
                .operatorNotes(utilization.getOperatorNotes())
                .status(utilization.getStatus())
                .createdAt(utilization.getCreatedAt())
                .build();
    }
}
