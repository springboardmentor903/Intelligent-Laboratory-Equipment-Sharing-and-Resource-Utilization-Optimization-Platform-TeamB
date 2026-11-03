package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdleTimeAnalysisDto {

    private Long totalAvailableMinutes;
    private Long totalBookedMinutes;
    private Long totalIdleMinutes;

    private Double totalAvailableHours;
    private Double totalBookedHours;
    private Double totalIdleHours;

    private Double idlePercentage;

    private List<DailyIdleDto> dailyBreakdown;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyIdleDto {
        private LocalDate date;
        private String dayName;
        private Long availableMinutes;
        private Long bookedMinutes;
        private Long idleMinutes;

        private Double availableHours;
        private Double bookedHours;
        private Double idleHours;
        private Double idlePercentage;
    }
}
