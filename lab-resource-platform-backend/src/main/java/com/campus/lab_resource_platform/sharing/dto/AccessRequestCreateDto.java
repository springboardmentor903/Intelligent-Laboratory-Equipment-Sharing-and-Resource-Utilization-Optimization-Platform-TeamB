package com.campus.lab_resource_platform.sharing.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;

@Getter
@Setter
@NoArgsConstructor
public class AccessRequestCreateDto {

    @NotNull(message = "Equipment ID is required")
    private Long equipmentId;

    @NotBlank(message = "Purpose is mandatory")
    @Size(max = 500, message = "Purpose must not exceed 500 characters")
    private String purpose;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime allowedStartTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime allowedEndTime;

    private Double estimatedUsageHours;

    public void setAllowedStartTime(Object value) {
        if (value instanceof LocalTime lt) {
            this.allowedStartTime = lt;
        } else if (value instanceof String s && !s.isBlank()) {
            String trimmed = s.trim();
            this.allowedStartTime = LocalTime.parse(trimmed.length() >= 5 ? trimmed.substring(0, 5) : trimmed);
        }
    }

    public void setAllowedEndTime(Object value) {
        if (value instanceof LocalTime lt) {
            this.allowedEndTime = lt;
        } else if (value instanceof String s && !s.isBlank()) {
            String trimmed = s.trim();
            this.allowedEndTime = LocalTime.parse(trimmed.length() >= 5 ? trimmed.substring(0, 5) : trimmed);
        }
    }

    public Double getEstimatedUsageHours() {
        if (this.estimatedUsageHours != null && this.estimatedUsageHours > 0) {
            return this.estimatedUsageHours;
        }
        if (allowedStartTime != null && allowedEndTime != null) {
            long minutes = Duration.between(allowedStartTime, allowedEndTime).toMinutes();
            return minutes > 0 ? Math.round((minutes / 60.0) * 10.0) / 10.0 : 0.0;
        }
        return null;
    }

    public void setRequestedStartDate(LocalDate requestedStartDate) {
        if (this.startDate == null) {
            this.startDate = requestedStartDate;
        }
    }

    public void setRequestedEndDate(LocalDate requestedEndDate) {
        if (this.endDate == null) {
            this.endDate = requestedEndDate;
        }
    }
}

