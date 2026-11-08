package com.campus.lab_resource_platform.booking.dto;

import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class BookingResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;

    private Long userId;
    private String userName;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BookingStatus status;

    private boolean isExternal;
    private Long sharingAgreementId;

    private BigDecimal feePerHour;
    private BigDecimal estimatedFee;
    private BigDecimal actualFee;

    private String purpose;
    private String rejectionReason;
    private String cancellationReason;

    private Instant createdAt;
    private Instant updatedAt;
}
