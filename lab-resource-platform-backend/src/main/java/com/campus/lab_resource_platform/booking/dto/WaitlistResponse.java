package com.campus.lab_resource_platform.booking.dto;

import com.campus.lab_resource_platform.booking.entity.WaitlistEntry;
import com.campus.lab_resource_platform.booking.entity.WaitlistStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private Long collegeId;
    private String collegeName;
    private Long userId;
    private String userName;
    private String userEmail;
    private Instant desiredStartTime;
    private Instant desiredEndTime;
    private String purpose;
    private WaitlistStatus status;
    private Integer priority;
    private Long queuePosition;
    private Long convertedBookingId;
    private Instant notifiedAt;
    private Instant createdAt;

    public static WaitlistResponse fromEntity(WaitlistEntry entity, Long queuePos) {
        if (entity == null) return null;

        Long colId = null;
        String colName = null;
        if (entity.getEquipment() != null) {
            colId = entity.getEquipment().getCollegeId() != null
                    ? entity.getEquipment().getCollegeId()
                    : entity.getEquipment().getInstitutionId();
        }

        return WaitlistResponse.builder()
                .id(entity.getId())
                .equipmentId(entity.getEquipment() != null ? entity.getEquipment().getId() : null)
                .equipmentName(entity.getEquipment() != null ? entity.getEquipment().getName() : null)
                .collegeId(colId)
                .collegeName(colName)
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userName(entity.getUser() != null ? entity.getUser().getFullName() : null)
                .userEmail(entity.getUser() != null ? entity.getUser().getEmail() : null)
                .desiredStartTime(entity.getDesiredStartTime())
                .desiredEndTime(entity.getDesiredEndTime())
                .purpose(entity.getPurpose())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .queuePosition(queuePos)
                .convertedBookingId(entity.getConvertedBookingId())
                .notifiedAt(entity.getNotifiedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

