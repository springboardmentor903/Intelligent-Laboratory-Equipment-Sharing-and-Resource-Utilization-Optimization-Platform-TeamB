package com.campus.lab_resource_platform.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderAssignDto {

    @NotNull(message = "Technician ID is required")
    private Long technicianId;

    private String notes;
}
