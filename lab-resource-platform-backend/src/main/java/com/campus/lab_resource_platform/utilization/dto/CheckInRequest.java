package com.campus.lab_resource_platform.utilization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRequest {

    private Long bookingId;

    private Long equipmentId;

    private LocalDateTime checkInTime;

    private String notes;
}
