package com.campus.lab_resource_platform.calibration.dto;

import com.campus.lab_resource_platform.maintenance.dto.WorkOrderResponse;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianTaskDto {

    private int totalTasks;
    private int overdueTasks;
    private Map<String, Integer> countsByStatus;
    private List<WorkOrderResponse> tasks;
}
