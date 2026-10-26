package com.campus.lab_resource_platform.booking.dto;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.*;

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
    private Instant createdAt;

    public static BookingResponse fromEntity(Booking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .equipmentId(b.getEquipment() != null ? b.getEquipment().getId() : null)
                .equipmentName(b.getEquipment() != null ? b.getEquipment().getName() : null)
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
                .createdAt(b.getCreatedAt())
                .build();
    }
}
