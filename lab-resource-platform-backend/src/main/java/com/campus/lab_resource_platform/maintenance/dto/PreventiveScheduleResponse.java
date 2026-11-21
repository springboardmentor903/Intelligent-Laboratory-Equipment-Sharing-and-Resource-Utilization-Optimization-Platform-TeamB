package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.PreventiveSchedule;
import com.campus.lab_resource_platform.maintenance.entity.ScheduleRepeatPeriod;
import com.campus.lab_resource_platform.maintenance.entity.ScheduleStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreventiveScheduleResponse {

    private Long id;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private String title;
    private String description;
    private ScheduleRepeatPeriod repeatPeriod;
    private Integer customRepeatDays;
    private LocalDate lastCompletedDate;
    private LocalDate nextDueDate;
    private Long daysUntilDue;
    private boolean active;
    private ScheduleStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static PreventiveScheduleResponse fromEntity(PreventiveSchedule s) {
        if (s == null) return null;
        LocalDate today = LocalDate.now();
        Long days = s.getNextDueDate() != null ? ChronoUnit.DAYS.between(today, s.getNextDueDate()) : null;

        return PreventiveScheduleResponse.builder()
                .id(s.getId())
                .equipmentId(s.getEquipment() != null ? s.getEquipment().getId() : null)
                .equipmentName(s.getEquipment() != null ? s.getEquipment().getName() : null)
                .assetTag(s.getEquipment() != null ? s.getEquipment().getAssetTag() : null)
                .title(s.getTitle())
                .description(s.getDescription())
                .repeatPeriod(s.getRepeatPeriod())
                .customRepeatDays(s.getCustomRepeatDays())
                .lastCompletedDate(s.getLastCompletedDate())
                .nextDueDate(s.getNextDueDate())
                .daysUntilDue(days)
                .active(s.isActive())
                .status(s.calculateStatus(today))
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
