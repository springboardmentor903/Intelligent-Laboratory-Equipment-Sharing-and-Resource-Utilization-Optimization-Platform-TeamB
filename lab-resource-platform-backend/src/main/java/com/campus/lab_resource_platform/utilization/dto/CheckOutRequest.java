package com.campus.lab_resource_platform.utilization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CheckOutRequest {

    @NotNull(message = "Booking ID is required")
    private Long bookingId;

    private LocalDateTime checkOutTime;

    private String notes;
}

