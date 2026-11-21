package com.campus.lab_resource_platform.maintenance.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderCompleteDto {

    @Size(max = 2000, message = "Resolution notes must not exceed 2000 characters")
    private String resolutionNotes;

    private BigDecimal cost;

    // Optional calibration extension (Task T2)
    private Boolean isCalibration;
    private LocalDate calibrationDate;
    private LocalDate nextCalibrationDate;
    private String calibrationResult;
    private String performedBy;
    private String certificateUrl;
    private String calibrationNotes;
}
