package com.campus.lab_resource_platform.utilization.dto;

import com.campus.lab_resource_platform.utilization.entity.Utilization;
import lombok.*;

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
    private Instant checkInTime;
    private Instant checkOutTime;
    private Long actualDurationMinutes;
    private String operatorNotes;
    private String status;
    private Instant createdAt;

    public static UtilizationResponse fromEntity(Utilization u, String colName) {
        if (u == null) return null;
        Long cId = null;
        if (u.getEquipment() != null) {
            cId = u.getEquipment().getCollegeId() != null
                    ? u.getEquipment().getCollegeId()
                    : u.getEquipment().getInstitutionId();
        }
        return UtilizationResponse.builder()
                .id(u.getId())
                .bookingId(u.getBooking() != null ? u.getBooking().getId() : null)
                .equipmentId(u.getEquipment() != null ? u.getEquipment().getId() : null)
                .equipmentName(u.getEquipment() != null ? u.getEquipment().getName() : null)
                .equipmentModel(u.getEquipment() != null ? u.getEquipment().getModel() : null)
                .equipmentCategory(u.getEquipment() != null ? u.getEquipment().getCategory() : null)
                .collegeId(cId)
                .collegeName(colName)
                .userId(u.getUser() != null ? u.getUser().getId() : null)
                .userName(u.getUser() != null ? u.getUser().getFullName() : null)
                .userEmail(u.getUser() != null ? u.getUser().getEmail() : null)
                .checkInTime(u.getCheckInTime())
                .checkOutTime(u.getCheckOutTime())
                .actualDurationMinutes(u.getActualDurationMinutes())
                .operatorNotes(u.getOperatorNotes())
                .status(u.getStatus())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
