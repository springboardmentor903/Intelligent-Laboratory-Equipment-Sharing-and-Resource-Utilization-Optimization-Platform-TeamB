package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.ScheduleRepeatPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreventiveScheduleDto {

    @NotNull(message = "Equipment ID is required")
    private Long equipmentId;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Repeat period is required")
    private ScheduleRepeatPeriod repeatPeriod;

    private Integer customRepeatDays;
    private LocalDate lastCompletedDate;
    private LocalDate nextDueDate;
}
