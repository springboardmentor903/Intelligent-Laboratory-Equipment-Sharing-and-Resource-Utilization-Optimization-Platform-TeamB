package com.campus.lab_resource_platform.booking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistRequest {

    @NotNull(message = "Equipment ID is required")
    private Long equipmentId;

    @NotNull(message = "Desired start time is required")
    private Instant desiredStartTime;

    @NotNull(message = "Desired end time is required")
    private Instant desiredEndTime;

    private String purpose;
}

