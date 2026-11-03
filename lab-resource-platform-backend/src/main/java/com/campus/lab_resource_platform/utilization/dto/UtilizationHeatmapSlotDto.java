package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilizationHeatmapSlotDto {

    private Integer dayOfWeek; // 1 = Monday, 7 = Sunday
    private String dayName;    // "Monday", etc.
    private Integer hour;      // 0 .. 23
    private String timeLabel;  // "09:00 - 10:00"

    private Long availableMinutes;
    private Long usedMinutes;
    private Double utilizationPercent; // null if availableMinutes == 0 (closed)
}
