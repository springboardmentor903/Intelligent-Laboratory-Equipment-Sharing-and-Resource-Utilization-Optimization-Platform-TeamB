package com.campus.lab_resource_platform.maintenance.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartPreviewResponse {

    private Long workOrderId;
    private String workOrderNumber;
    private Long equipmentId;
    private String equipmentName;
    private Instant maintenanceStartDate;
    private Instant maintenanceEndDate;
    private int totalConflictedBookings;
    private List<ConflictedBookingDto> conflictedBookings;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConflictedBookingDto {
        private Long bookingId;
        private Long userId;
        private String userName;
        private String userEmail;
        private Instant startTime;
        private Instant endTime;
        private String status;
        private String purpose;
    }
}
