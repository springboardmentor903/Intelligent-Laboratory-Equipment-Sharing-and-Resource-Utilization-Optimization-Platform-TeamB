package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderCreateDto {

    @NotNull(message = "Equipment ID is required")
    private Long equipmentId;

    @NotNull(message = "Work order type is required")
    private WorkOrderType type;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Priority is required")
    private MaintenancePriority priority;

    private Long assignedTechnicianId;
    private Instant expectedStartDate;
    private Instant expectedEndDate;
    private BigDecimal estimatedCost;
    private Long maintenanceRequestId;
    private Long preventiveScheduleId;
}
