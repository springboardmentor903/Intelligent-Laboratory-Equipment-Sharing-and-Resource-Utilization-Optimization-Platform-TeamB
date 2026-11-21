package com.campus.lab_resource_platform.calibration.service;

import com.campus.lab_resource_platform.calibration.dto.CalibrationReminderDto;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.WorkOrderCreateDto;
import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import com.campus.lab_resource_platform.maintenance.service.WorkOrderService;
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
public class CalibrationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CalibrationScheduler.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final CalibrationTrackingService calibrationService;
    private final WorkOrderService workOrderService;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public CalibrationScheduler(
            CalibrationTrackingService calibrationService,
            WorkOrderService workOrderService,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository) {
        this.calibrationService = calibrationService;
        this.workOrderService = workOrderService;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Daily scheduled job at 1:15 AM IST to identify equipment due for calibration and create work orders.
     */
    @Scheduled(cron = "0 15 1 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void processCalibrationDueWorkOrders() {
        log.info("Running daily Calibration Scheduler at 1:15 AM IST");

        List<User> managers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.LAB_MANAGER)
                .toList();

        if (managers.isEmpty()) {
            log.warn("No Lab Managers found to run calibration scheduler");
            return;
        }

        for (User manager : managers) {
            try {
                List<CalibrationReminderDto> reminders = calibrationService.getReminders(manager.getEmail());
                for (CalibrationReminderDto reminder : reminders) {
                    if (reminder.isActiveWorkOrderExists()) {
                        continue;
                    }

                    Equipment eq = equipmentRepository.findById(reminder.getEquipmentId()).orElse(null);
                    if (eq == null) continue;

                    WorkOrderCreateDto woDto = WorkOrderCreateDto.builder()
                            .equipmentId(eq.getId())
                            .type(WorkOrderType.CALIBRATION)
                            .title("Routine Calibration: " + eq.getName())
                            .description("Auto-generated calibration work order. Equipment is " +
                                    reminder.getCalibrationState() + ". Next due date: " + reminder.getNextDueDate())
                            .priority(MaintenancePriority.HIGH)
                            .expectedStartDate(LocalDate.now(IST_ZONE).atTime(9, 0).atZone(IST_ZONE).toInstant())
                            .expectedEndDate(LocalDate.now(IST_ZONE).plusDays(2).atTime(17, 0).atZone(IST_ZONE).toInstant())
                            .build();

                    WorkOrder wo = workOrderService.createWorkOrderInternal(woDto, manager);
                    log.info("Auto-created CALIBRATION work order {} for equipment {} (#{})",
                            wo.getWorkOrderNumber(), eq.getName(), eq.getId());
                }
            } catch (Exception ex) {
                log.error("Error processing calibration reminders for manager {}: {}", manager.getEmail(), ex.getMessage());
            }
        }
    }
}
