package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderResponse {

    private Long id;
    private String workOrderNumber;
    private WorkOrderType type;
    private WorkOrderStatus status;
    private Long equipmentId;
    private String equipmentName;
    private String assetTag;
    private Long maintenanceRequestId;
    private Long preventiveScheduleId;
    private Long assignedTechnicianId;
    private String assignedTechnicianName;
    private String assignedTechnicianEmail;
    private Long createdById;
    private String createdByName;
    private String title;
    private String description;
    private MaintenancePriority priority;
    private Instant expectedStartDate;
    private Instant expectedEndDate;
    private Instant actualStartDate;
    private Instant actualEndDate;
    private BigDecimal cost;
    private String resolutionNotes;
    private boolean overdue;
    private List<WorkOrderServiceLogDto> serviceLogs;
    private Instant createdAt;
    private Instant updatedAt;

    public static WorkOrderResponse fromEntity(WorkOrder w) {
        if (w == null) return null;

        boolean isOverdue = false;
        if (w.getExpectedEndDate() != null && Instant.now().isAfter(w.getExpectedEndDate())
                && (w.getStatus() == WorkOrderStatus.OPEN || w.getStatus() == WorkOrderStatus.ASSIGNED || w.getStatus() == WorkOrderStatus.IN_PROGRESS)) {
            isOverdue = true;
        }

        List<WorkOrderServiceLogDto> logs = w.getServiceLogs() != null
                ? w.getServiceLogs().stream().map(WorkOrderServiceLogDto::fromEntity).toList()
                : Collections.emptyList();

        return WorkOrderResponse.builder()
                .id(w.getId())
                .workOrderNumber(w.getWorkOrderNumber())
                .type(w.getType())
                .status(w.getStatus())
                .equipmentId(w.getEquipment() != null ? w.getEquipment().getId() : null)
                .equipmentName(w.getEquipment() != null ? w.getEquipment().getName() : null)
                .assetTag(w.getEquipment() != null ? w.getEquipment().getAssetTag() : null)
                .maintenanceRequestId(w.getMaintenanceRequest() != null ? w.getMaintenanceRequest().getId() : null)
                .preventiveScheduleId(w.getPreventiveSchedule() != null ? w.getPreventiveSchedule().getId() : null)
                .assignedTechnicianId(w.getAssignedTechnician() != null ? w.getAssignedTechnician().getId() : null)
                .assignedTechnicianName(w.getAssignedTechnician() != null ? w.getAssignedTechnician().getFullName() : null)
                .assignedTechnicianEmail(w.getAssignedTechnician() != null ? w.getAssignedTechnician().getEmail() : null)
                .createdById(w.getCreatedBy() != null ? w.getCreatedBy().getId() : null)
                .createdByName(w.getCreatedBy() != null ? w.getCreatedBy().getFullName() : null)
                .title(w.getTitle())
                .description(w.getDescription())
                .priority(w.getPriority())
                .expectedStartDate(w.getExpectedStartDate())
                .expectedEndDate(w.getExpectedEndDate())
                .actualStartDate(w.getActualStartDate())
                .actualEndDate(w.getActualEndDate())
                .cost(w.getCost())
                .resolutionNotes(w.getResolutionNotes())
                .overdue(isOverdue)
                .serviceLogs(logs)
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }
}
