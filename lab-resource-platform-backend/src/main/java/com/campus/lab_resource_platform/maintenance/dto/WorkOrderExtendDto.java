package com.campus.lab_resource_platform.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderExtendDto {

    @NotNull(message = "New expected end date is required")
    private Instant newExpectedEndDate;

    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;
}
