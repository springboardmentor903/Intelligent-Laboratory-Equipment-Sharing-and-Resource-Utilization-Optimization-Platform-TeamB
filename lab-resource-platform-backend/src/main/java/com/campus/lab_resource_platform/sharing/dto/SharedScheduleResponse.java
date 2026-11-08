package com.campus.lab_resource_platform.sharing.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SharedScheduleResponse {

    private Long equipmentId;
    private String equipmentName;
    private LocalDate startDate;
    private LocalDate endDate;

    private Long weeklyExternalShareLimitMinutes;
    private Long weeklyExternalMinutesUsed;

    private AllowedBookingWindow allowedBookingWindow;

    private List<OccupiedSlot> occupiedSlots = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OccupiedSlot {
        private Long slotId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String type; // "INTERNAL", "EXTERNAL", "HOLD"
        // Visible ONLY to owner's Lab Manager & Institution Administrator
        private String partnerInstitutionName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AllowedBookingWindow {
        private Long agreementId;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalTime allowedStartTime;
        private LocalTime allowedEndTime;
    }
}

