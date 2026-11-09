package com.campus.lab_resource_platform.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CalibrationRecordRequest {

    @NotNull(message = "Calibration date is required")
    @PastOrPresent(message = "Calibration date cannot be in the future")
    private LocalDate calibrationDate;

    @FutureOrPresent(message = "Next calibration date cannot be in the past")
    private LocalDate nextCalibrationDate;

    @NotBlank(message = "Calibration result is required")
    @Size(max = 100)
    private String result;

    @Size(max = 150)
    private String performedBy;

    @Size(max = 2000)
    private String notes;
}