package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeakUsageDto {

    private String busiestDayOfWeek;
    private Map<String, Long> dayOfWeekUsage; // "MONDAY" -> minutes
    private List<Integer> peakHoursOfDay;
    private Map<Integer, Long> hourlyUsage;    // 0 .. 23 -> minutes
    private List<BusiestSlotDto> top5Slots;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BusiestSlotDto {
        private Integer dayOfWeek;
        private String dayName;
        private Integer hour;
        private String timeLabel;
        private Long usedMinutes;
        private Long availableMinutes;
        private Double utilizationPercent;
    }
}
