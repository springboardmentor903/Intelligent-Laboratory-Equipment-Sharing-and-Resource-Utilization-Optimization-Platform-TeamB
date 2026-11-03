package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationBenchmarkDto {

    private Double currentPeriodUtilization;
    private Double benchmarkPeriodUtilization;
    private Double differencePercentagePoints;
    private String comparisonStatus; // HIGHER, LOWER, STABLE

    private LocalDate currentStartDate;
    private LocalDate currentEndDate;
    private LocalDate benchmarkStartDate;
    private LocalDate benchmarkEndDate;
}
