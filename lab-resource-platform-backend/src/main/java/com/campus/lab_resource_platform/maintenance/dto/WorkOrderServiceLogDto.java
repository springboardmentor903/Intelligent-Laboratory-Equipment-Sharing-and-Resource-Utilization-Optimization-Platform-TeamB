package com.campus.lab_resource_platform.maintenance.dto;

import com.campus.lab_resource_platform.maintenance.entity.WorkOrderServiceLog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderServiceLogDto {

    private Long id;
    private Long workOrderId;
    private Long authorId;
    private String authorName;
    private String action;

    @NotBlank(message = "Notes cannot be empty")
    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;

    private Instant timestamp;

    public static WorkOrderServiceLogDto fromEntity(WorkOrderServiceLog log) {
        if (log == null) return null;
        return WorkOrderServiceLogDto.builder()
                .id(log.getId())
                .workOrderId(log.getWorkOrder() != null ? log.getWorkOrder().getId() : null)
                .authorId(log.getAuthor() != null ? log.getAuthor().getId() : null)
                .authorName(log.getAuthor() != null ? log.getAuthor().getFullName() : null)
                .action(log.getAction())
                .notes(log.getNotes())
                .timestamp(log.getTimestamp())
                .build();
    }
}
