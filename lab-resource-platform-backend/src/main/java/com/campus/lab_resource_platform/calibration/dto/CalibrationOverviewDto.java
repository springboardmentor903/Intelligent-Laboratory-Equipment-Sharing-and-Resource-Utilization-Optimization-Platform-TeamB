package com.campus.lab_resource_platform.calibration.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationOverviewDto {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long departmentId;
    private String departmentName;
    private String model;
    private String location;
    private LocalDate latestCalibrationDate;
    private LocalDate nextDueDate;
    private Long daysUntilDue;
    private CalibrationState calibrationState;
    private String lastResult;
    private String lastPerformedBy;
    private String latestCertificateUrl;
}
