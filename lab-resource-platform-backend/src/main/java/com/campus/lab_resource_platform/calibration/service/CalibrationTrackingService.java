package com.campus.lab_resource_platform.calibration.service;

import com.campus.lab_resource_platform.calibration.dto.CalibrationOverviewDto;
import com.campus.lab_resource_platform.calibration.dto.CalibrationReminderDto;
import com.campus.lab_resource_platform.calibration.dto.CalibrationState;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CalibrationTrackingService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final EquipmentRepository equipmentRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;

    public CalibrationTrackingService(
            EquipmentRepository equipmentRepository,
            CalibrationRecordRepository calibrationRecordRepository,
            DepartmentRepository departmentRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository) {
        this.equipmentRepository = equipmentRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.departmentRepository = departmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<CalibrationOverviewDto> getCalibrationOverview(
            Long departmentId,
            CalibrationState stateFilter,
            String keyword,
            String userEmail,
            Pageable pageable) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        List<Equipment> equipmentList = equipmentRepository.findByInstitutionIdOrCollegeId(instId, instId);
        LocalDate today = LocalDate.now(IST_ZONE);

        List<CalibrationOverviewDto> overviewList = new ArrayList<>();

        for (Equipment eq : equipmentList) {
            if (departmentId != null && !Objects.equals(eq.getDepartmentId(), departmentId)) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase().trim();
                boolean matchesName = eq.getName() != null && eq.getName().toLowerCase().contains(kw);
                boolean matchesTag = eq.getAssetTag() != null && eq.getAssetTag().toLowerCase().contains(kw);
                boolean matchesModel = eq.getModel() != null && eq.getModel().toLowerCase().contains(kw);
                if (!matchesName && !matchesTag && !matchesModel) {
                    continue;
                }
            }

            List<CalibrationRecord> records = calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(eq.getId());
            CalibrationRecord latest = records.isEmpty() ? null : records.get(0);

            CalibrationState state;
            Long daysUntilDue = null;
            LocalDate calDate = null;
            LocalDate nextDue = null;
            String result = null;
            String performedBy = null;

            if (latest == null) {
                state = CalibrationState.NONE;
            } else {
                calDate = latest.getCalibrationDate();
                nextDue = latest.getNextCalibrationDate();
                result = latest.getResult();
                performedBy = latest.getPerformedBy();

                if (nextDue == null) {
                    state = CalibrationState.VALID;
                } else {
                    daysUntilDue = ChronoUnit.DAYS.between(today, nextDue);
                    if (nextDue.isBefore(today)) {
                        state = CalibrationState.EXPIRED;
                    } else if (daysUntilDue <= 30) {
                        state = CalibrationState.DUE_SOON;
                    } else {
                        state = CalibrationState.VALID;
                    }
                }
            }

            if (stateFilter != null && state != stateFilter) {
                continue;
            }

            String deptName = null;
            if (eq.getDepartmentId() != null) {
                deptName = departmentRepository.findById(eq.getDepartmentId())
                        .map(Department::getName).orElse("Department " + eq.getDepartmentId());
            }

            overviewList.add(CalibrationOverviewDto.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .departmentId(eq.getDepartmentId())
                    .departmentName(deptName)
                    .model(eq.getModel())
                    .location(eq.getLocation())
                    .latestCalibrationDate(calDate)
                    .nextDueDate(nextDue)
                    .daysUntilDue(daysUntilDue)
                    .calibrationState(state)
                    .lastResult(result)
                    .lastPerformedBy(performedBy)
                    .build());
        }

        // Apply manual in-memory pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), overviewList.size());
        List<CalibrationOverviewDto> pageContent = start <= end ? overviewList.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(pageContent, pageable, overviewList.size());
    }

    @Transactional(readOnly = true)
    public List<CalibrationReminderDto> getReminders(String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        List<Equipment> equipmentList = equipmentRepository.findByInstitutionIdOrCollegeId(instId, instId);
        LocalDate today = LocalDate.now(IST_ZONE);

        List<CalibrationReminderDto> reminders = new ArrayList<>();

        for (Equipment eq : equipmentList) {
            List<CalibrationRecord> records = calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(eq.getId());
            if (records.isEmpty()) continue;

            CalibrationRecord latest = records.get(0);
            LocalDate nextDue = latest.getNextCalibrationDate();
            if (nextDue == null) continue;

            long daysUntilDue = ChronoUnit.DAYS.between(today, nextDue);
            CalibrationState state = null;
            String message = null;

            if (nextDue.isBefore(today)) {
                state = CalibrationState.EXPIRED;
                message = "Calibration expired " + Math.abs(daysUntilDue) + " day(s) ago on " + nextDue;
            } else if (daysUntilDue <= 30) {
                state = CalibrationState.DUE_SOON;
                message = "Calibration due soon in " + daysUntilDue + " day(s) on " + nextDue;
            }

            if (state != null) {
                boolean activeWo = workOrderRepository.existsActiveWorkOrderByEquipmentAndType(
                        eq.getId(),
                        WorkOrderType.CALIBRATION,
                        List.of(WorkOrderStatus.OPEN, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS)
                );

                reminders.add(CalibrationReminderDto.builder()
                        .equipmentId(eq.getId())
                        .equipmentName(eq.getName())
                        .assetTag(eq.getAssetTag())
                        .location(eq.getLocation())
                        .latestCalibrationDate(latest.getCalibrationDate())
                        .nextDueDate(nextDue)
                        .daysUntilDue(daysUntilDue)
                        .calibrationState(state)
                        .activeWorkOrderExists(activeWo)
                        .message(message)
                        .build());
            }
        }

        reminders.sort(Comparator.comparing(CalibrationReminderDto::getDaysUntilDue));
        return reminders;
    }

    private void validateNotSystemAdmin(User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ForbiddenException("System Administrators do not have access to maintenance and calibration operations");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
