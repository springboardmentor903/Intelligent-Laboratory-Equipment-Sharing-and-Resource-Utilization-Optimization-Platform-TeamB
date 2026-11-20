package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.maintenance.dto.WorkOrderCreateDto;
import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.PreventiveSchedule;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import com.campus.lab_resource_platform.maintenance.repository.PreventiveScheduleRepository;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final PreventiveScheduleRepository scheduleRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final UserRepository userRepository;

    public MaintenanceScheduler(
            PreventiveScheduleRepository scheduleRepository,
            WorkOrderRepository workOrderRepository,
            WorkOrderService workOrderService,
            UserRepository userRepository) {
        this.scheduleRepository = scheduleRepository;
        this.workOrderRepository = workOrderRepository;
        this.workOrderService = workOrderService;
        this.userRepository = userRepository;
    }

    /**
     * Daily scheduled job at 1:00 AM IST to create preventive work orders when due within 7 days.
     */
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void processPreventiveMaintenanceDueSoon() {
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalDate cutoff = today.plusDays(7);
        log.info("Running daily Preventive Maintenance Scheduler at 1:00 AM IST for cutoff {}", cutoff);

        List<PreventiveSchedule> dueSchedules = scheduleRepository.findDueSchedules(cutoff);
        log.info("Found {} active schedules due within 7 days", dueSchedules.size());

        for (PreventiveSchedule schedule : dueSchedules) {
            // Check for duplicate active work order
            List<WorkOrderStatus> activeStatuses = List.of(
                    WorkOrderStatus.OPEN,
                    WorkOrderStatus.ASSIGNED,
                    WorkOrderStatus.IN_PROGRESS
            );
            var existing = workOrderRepository.findByPreventiveScheduleIdAndStatusIn(schedule.getId(), activeStatuses);
            if (!existing.isEmpty()) {
                log.info("Schedule #{} already has an active work order ({}). Skipping duplicate creation.",
                        schedule.getId(), existing.get(0).getWorkOrderNumber());
                continue;
            }

            // Find an appropriate manager or system user as creator
            User creator = schedule.getEquipment().getInstitutionId() != null
                    ? userRepository.findFirstByInstitutionIdAndRole(schedule.getEquipment().getInstitutionId(), User.Role.LAB_MANAGER)
                    .orElse(null)
                    : null;
            if (creator == null) {
                creator = userRepository.findFirstByRole(User.Role.LAB_MANAGER)
                        .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
            }
            if (creator == null) {
                log.warn("Cannot create preventive work order for schedule #{}: no user found", schedule.getId());
                continue;
            }

            WorkOrderCreateDto woDto = WorkOrderCreateDto.builder()
                    .equipmentId(schedule.getEquipment().getId())
                    .type(WorkOrderType.PREVENTIVE)
                    .title("Preventive Maintenance: " + schedule.getTitle())
                    .description("Auto-generated scheduled preventive maintenance due on " + schedule.getNextDueDate() +
                            ". Description: " + schedule.getDescription())
                    .priority(MaintenancePriority.MEDIUM)
                    .preventiveScheduleId(schedule.getId())
                    .expectedStartDate(schedule.getNextDueDate().atTime(9, 0).atZone(IST_ZONE).toInstant())
                    .expectedEndDate(schedule.getNextDueDate().atTime(17, 0).atZone(IST_ZONE).toInstant())
                    .build();

            try {
                var wo = workOrderService.createWorkOrderInternal(woDto, creator);
                log.info("Created PREVENTIVE work order {} for schedule #{}", wo.getWorkOrderNumber(), schedule.getId());
            } catch (Exception ex) {
                log.error("Failed to create work order for schedule #{}: {}", schedule.getId(), ex.getMessage());
            }
        }
    }
}
