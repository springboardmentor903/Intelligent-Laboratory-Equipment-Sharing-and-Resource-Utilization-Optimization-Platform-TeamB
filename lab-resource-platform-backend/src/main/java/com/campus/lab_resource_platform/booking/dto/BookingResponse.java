package com.campus.lab_resource_platform.booking.dto;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {

    private Long id;

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;

    private Long collegeId;
    private String collegeName;

    private Long userId;
    private String userName;
    private String userEmail;

    private Instant startTime;
    private Instant endTime;

    private String purpose;
    private BookingStatus status;

    private Long approverId;
    private String approverName;
    private Instant approvedAt;

    private String rejectionReason;
    private Instant cancelledAt;
    private String cancellationReason;

    // T5: Inter-institution sharing and external booking
    private boolean external;
    private Long sharingAgreementId;
    private BigDecimal feePerHour;
    private BigDecimal estimatedFee;
    private BigDecimal actualFee;

    // Origin: recurring booking support
    private Boolean isRecurring;
    private String recurrenceGroupId;
    private String recurrenceType;

    private Instant createdAt;
    private Instant updatedAt;

    public static BookingResponse fromEntity(Booking b) {
        Long collegeId = null;

        if (b.getEquipment() != null) {
            collegeId = b.getEquipment().getCollegeId() != null
                    ? b.getEquipment().getCollegeId()
                    : b.getEquipment().getInstitutionId();
        }

        return BookingResponse.builder()
                .id(b.getId())
                .equipmentId(b.getEquipment() != null ? b.getEquipment().getId() : null)
                .equipmentName(b.getEquipment() != null ? b.getEquipment().getName() : null)
                .assetTag(b.getEquipment() != null ? b.getEquipment().getAssetTag() : null)
                .collegeId(collegeId)
                .collegeName(null)
                .userId(b.getUser() != null ? b.getUser().getId() : null)
                .userName(b.getUser() != null ? b.getUser().getFullName() : null)
                .userEmail(b.getUser() != null ? b.getUser().getEmail() : null)
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .purpose(b.getPurpose())
                .status(b.getStatus())
                .approverId(b.getApprover() != null ? b.getApprover().getId() : null)
                .approverName(b.getApprover() != null ? b.getApprover().getFullName() : null)
                .approvedAt(b.getApprovedAt())
                .rejectionReason(b.getRejectionReason())
                .cancelledAt(b.getCancelledAt())
                .cancellationReason(b.getCancellationReason())
                .external(b.isExternal())
                .sharingAgreementId(b.getSharingAgreementId())
                .feePerHour(b.getFeePerHour())
                .estimatedFee(b.getEstimatedFee())
                .actualFee(b.getActualFee())
                .isRecurring(b.getIsRecurring())
                .recurrenceGroupId(b.getRecurrenceGroupId())
                .recurrenceType(b.getRecurrenceType())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}