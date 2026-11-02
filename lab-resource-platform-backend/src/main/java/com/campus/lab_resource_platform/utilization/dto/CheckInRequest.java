package com.campus.lab_resource_platform.utilization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRequest {

    private Long bookingId;

    @NotNull(message = "equipmentId is required")
    private Long equipmentId;

    private String notes;
}

