package com.campus.lab_resource_platform.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectBookingRequest {

    @NotBlank(message = "Rejection reason is required")
    private String reason;
}
