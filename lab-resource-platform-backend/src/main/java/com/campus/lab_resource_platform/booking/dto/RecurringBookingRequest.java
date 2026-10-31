package com.campus.lab_resource_platform.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringBookingRequest {

    @NotNull(message = "Equipment ID is required")
    private Long equipmentId;

    @NotNull(message = "Start time is required")
    private Instant startTime;

    @NotNull(message = "End time is required")
    private Instant endTime;

    private String purpose;

    /**
     * DAILY or WEEKLY
     */
    @Builder.Default
    private String recurrenceType = "WEEKLY";

    /**
     * Number of repeating occurrences (e.g., 2 to 12 sessions)
     */
    @Min(value = 1, message = "Must have at least 1 occurrence")
    @Max(value = 16, message = "Cannot exceed 16 occurrences")
    @Builder.Default
    private Integer occurrences = 4;
}

