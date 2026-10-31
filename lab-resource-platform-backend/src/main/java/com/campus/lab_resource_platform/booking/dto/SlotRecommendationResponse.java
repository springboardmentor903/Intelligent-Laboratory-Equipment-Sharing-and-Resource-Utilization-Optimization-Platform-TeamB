package com.campus.lab_resource_platform.booking.dto;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotRecommendationResponse {

    private Long equipmentId;
    private String equipmentName;
    private String queryDate;
    private int durationMinutes;
    @Builder.Default
    private List<RecommendedSlotDto> recommendations = new ArrayList<>();
    private String message;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendedSlotDto {
        private Instant startTime;
        private Instant endTime;
        private int score; // 0 to 100
        private String reason;
        private boolean isContiguous;
        private boolean isOffPeak;
    }
}

