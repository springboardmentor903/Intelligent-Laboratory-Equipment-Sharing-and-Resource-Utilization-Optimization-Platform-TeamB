package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentMaintenanceHistoryResponse {

    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private int totalWorkOrdersCompleted;
    private Long totalDowntimeMinutes;
    private BigDecimal totalMaintenanceCost;
    private List<HistoryItemDto> historyItems;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryItemDto {
        private Long workOrderId;
        private String workOrderNumber;
        private WorkOrderType type;
        private WorkOrderStatus status;
        private MaintenancePriority priority;
        private String title;
        private String description;
        private String resolutionNotes;
        private Long technicianId;
        private String technicianName;
        private String technicianEmail;
        private Instant actualStartDate;
        private Instant actualEndDate;
        private Long downtimeMinutes;
        private BigDecimal cost;
    }
}
