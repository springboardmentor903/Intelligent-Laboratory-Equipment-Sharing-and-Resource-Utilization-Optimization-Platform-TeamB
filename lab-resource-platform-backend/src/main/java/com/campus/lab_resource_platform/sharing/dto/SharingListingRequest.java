package com.campus.lab_resource_platform.sharing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class SharingListingRequest {

    @NotNull(message = "Equipment ID is required")
    private Long equipmentId;

    @NotNull(message = "Fee per hour is required")
    @DecimalMin(value = "0.0", message = "Fee per hour cannot be negative")
    private BigDecimal feePerHour;

    @NotNull(message = "External share percentage is required")
    @DecimalMin(value = "0.0", message = "External share percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "External share percentage cannot exceed 100")
    private BigDecimal externalSharePercentage;

    @NotNull(message = "Minimum notice hours is required")
    @Min(value = 0, message = "Minimum notice hours cannot be negative")
    private Integer minNoticeHours = 0;

    @NotNull(message = "Maximum booking duration in hours is required")
    @Min(value = 1, message = "Maximum booking duration must be at least 1 hour")
    private Integer maxBookingDurationHours;

    @Size(max = 3000, message = "Sharing terms cannot exceed 3000 characters")
    private String sharingTerms;

    private Boolean active = true;
}

