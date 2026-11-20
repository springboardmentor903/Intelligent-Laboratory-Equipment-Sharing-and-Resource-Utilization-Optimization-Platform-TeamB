package com.campus.lab_resource_platform.calibration.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationReminderDto {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private String location;
    private LocalDate latestCalibrationDate;
    private LocalDate nextDueDate;
    private Long daysUntilDue;
    private CalibrationState calibrationState;
    private boolean activeWorkOrderExists;
    private String message;
}
