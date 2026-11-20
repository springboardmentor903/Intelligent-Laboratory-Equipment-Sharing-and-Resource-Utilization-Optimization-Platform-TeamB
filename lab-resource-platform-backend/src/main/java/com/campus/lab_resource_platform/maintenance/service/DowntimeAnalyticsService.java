package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.DowntimeSummaryResponse;
import com.campus.lab_resource_platform.maintenance.dto.EquipmentMaintenanceHistoryResponse;
import com.campus.lab_resource_platform.maintenance.entity.EquipmentDowntime;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.repository.EquipmentDowntimeRepository;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DowntimeAnalyticsService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentDowntimeRepository downtimeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DowntimeAnalyticsService(
            EquipmentRepository equipmentRepository,
            WorkOrderRepository workOrderRepository,
            EquipmentDowntimeRepository downtimeRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository) {
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.downtimeRepository = downtimeRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public EquipmentMaintenanceHistoryResponse getEquipmentMaintenanceHistory(Long equipmentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + equipmentId));

        validateEquipmentAccess(user, equipment);

        List<WorkOrder> completed = workOrderRepository
                .findByEquipmentIdAndStatusOrderByActualEndDateDesc(equipmentId, WorkOrderStatus.COMPLETED);

        long totalDowntime = 0L;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<EquipmentMaintenanceHistoryResponse.HistoryItemDto> items = new ArrayList<>();

        for (WorkOrder wo : completed) {
            long mins = 0L;
            if (wo.getActualStartDate() != null && wo.getActualEndDate() != null) {
                mins = Math.max(0, Duration.between(wo.getActualStartDate(), wo.getActualEndDate()).toMinutes());
            }
            totalDowntime += mins;

            if (wo.getCost() != null) {
                totalCost = totalCost.add(wo.getCost());
            }

            items.add(EquipmentMaintenanceHistoryResponse.HistoryItemDto.builder()
                    .workOrderId(wo.getId())
                    .workOrderNumber(wo.getWorkOrderNumber())
                    .type(wo.getType())
                    .status(wo.getStatus())
                    .priority(wo.getPriority())
                    .title(wo.getTitle())
                    .description(wo.getDescription())
                    .resolutionNotes(wo.getResolutionNotes())
                    .technicianId(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getId() : null)
                    .technicianName(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getFullName() : null)
                    .technicianEmail(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getEmail() : null)
                    .actualStartDate(wo.getActualStartDate())
                    .actualEndDate(wo.getActualEndDate())
                    .downtimeMinutes(mins)
                    .cost(wo.getCost())
                    .build());
        }

        return EquipmentMaintenanceHistoryResponse.builder()
                .equipmentId(equipment.getId())
                .equipmentName(equipment.getName())
                .assetTag(equipment.getAssetTag())
                .totalWorkOrdersCompleted(completed.size())
                .totalDowntimeMinutes(totalDowntime)
                .totalMaintenanceCost(totalCost)
                .historyItems(items)
                .build();
    }

    @Transactional(readOnly = true)
    public DowntimeSummaryResponse getDowntimeSummary(
            String scope,
            Long scopeId,
            String period,
            String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        Instant since = resolveSince(period);
        String resolvedScope = scope != null ? scope.toUpperCase() : "INSTITUTION";

        Long filterDeptId = null;
        Long filterEquipmentId = null;

        if ("EQUIPMENT".equals(resolvedScope)) {
            filterEquipmentId = scopeId;
        } else if ("DEPARTMENT".equals(resolvedScope)) {
            filterDeptId = scopeId != null ? scopeId : user.getDepartmentId();
        }

        List<EquipmentDowntime> records = downtimeRepository.findDowntimesInRange(
                instId, filterDeptId, filterEquipmentId, since);

        long totalRaw = 0L;
        long totalOpenHours = 0L;
        Set<Long> woIds = new HashSet<>();

        Map<Long, List<EquipmentDowntime>> byEquip = records.stream()
                .collect(Collectors.groupingBy(d -> d.getEquipment().getId()));

        List<DowntimeSummaryResponse.EquipmentDowntimeDetailDto> details = new ArrayList<>();

        // Estimate open hours in period (e.g., 30 days * 10h/day * 60m = 18,000 mins per equipment)
        long daysInPeriod = Math.max(1, ChronoUnit.DAYS.between(since.atZone(IST_ZONE).toLocalDate(), LocalDate.now(IST_ZONE)));
        long totalPossibleOpenMinsPerEquip = daysInPeriod * 10 * 60;

        for (Map.Entry<Long, List<EquipmentDowntime>> entry : byEquip.entrySet()) {
            Long eqId = entry.getKey();
            List<EquipmentDowntime> dtList = entry.getValue();
            Equipment eq = dtList.get(0).getEquipment();

            long eqRaw = 0L;
            long eqOpen = 0L;
            Set<Long> eqWoIds = new HashSet<>();
            BigDecimal eqCost = BigDecimal.ZERO;

            for (EquipmentDowntime d : dtList) {
                long raw = d.getTotalDowntimeMinutes() != null ? d.getTotalDowntimeMinutes() : 0L;
                long open = d.getOpenHoursDowntimeMinutes() != null ? d.getOpenHoursDowntimeMinutes() : (raw * 10 / 24);
                eqRaw += raw;
                eqOpen += open;
                if (d.getWorkOrder() != null) {
                    eqWoIds.add(d.getWorkOrder().getId());
                    if (d.getWorkOrder().getCost() != null) {
                        eqCost = eqCost.add(d.getWorkOrder().getCost());
                    }
                }
            }

            totalRaw += eqRaw;
            totalOpenHours += eqOpen;
            woIds.addAll(eqWoIds);

            double lostPct = totalPossibleOpenMinsPerEquip > 0
                    ? Math.min(100.0, (eqOpen * 100.0) / totalPossibleOpenMinsPerEquip)
                    : 0.0;

            String deptName = null;
            if (eq.getDepartmentId() != null) {
                deptName = departmentRepository.findById(eq.getDepartmentId())
                        .map(Department::getName).orElse("Department " + eq.getDepartmentId());
            }

            details.add(DowntimeSummaryResponse.EquipmentDowntimeDetailDto.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .departmentId(eq.getDepartmentId())
                    .departmentName(deptName)
                    .workOrdersCount(eqWoIds.size())
                    .rawDowntimeMinutes(eqRaw)
                    .openHoursDowntimeMinutes(eqOpen)
                    .percentageOfOpenHoursLost(Math.round(lostPct * 10.0) / 10.0)
                    .maintenanceCost(eqCost)
                    .build());
        }

        int woCount = woIds.size();
        double avgDowntime = woCount > 0 ? (double) totalRaw / woCount : 0.0;

        long totalPossibleOpenMinsAll = details.size() * totalPossibleOpenMinsPerEquip;
        double overallLostPct = totalPossibleOpenMinsAll > 0
                ? Math.min(100.0, (totalOpenHours * 100.0) / totalPossibleOpenMinsAll)
                : 0.0;

        return DowntimeSummaryResponse.builder()
                .scope(resolvedScope)
                .scopeId(scopeId)
                .period(period != null ? period : "LAST_30_DAYS")
                .totalRawDowntimeMinutes(totalRaw)
                .openHoursDowntimeMinutes(totalOpenHours)
                .totalWorkOrdersCount(woCount)
                .averageDowntimeMinutesPerOrder(Math.round(avgDowntime * 10.0) / 10.0)
                .openHoursLostPercentage(Math.round(overallLostPct * 10.0) / 10.0)
                .equipmentDowntimes(details)
                .build();
    }

    private Instant resolveSince(String period) {
        if (period == null) return Instant.now().minus(Duration.ofDays(30));
        return switch (period.toUpperCase()) {
            case "LAST_7_DAYS", "7_DAYS" -> Instant.now().minus(Duration.ofDays(7));
            case "LAST_30_DAYS", "30_DAYS", "MONTH" -> Instant.now().minus(Duration.ofDays(30));
            case "LAST_90_DAYS", "90_DAYS", "QUARTER" -> Instant.now().minus(Duration.ofDays(90));
            case "LAST_YEAR", "YEAR" -> Instant.now().minus(Duration.ofDays(365));
            default -> Instant.now().minus(Duration.ofDays(30));
        };
    }

    private void validateNotSystemAdmin(User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ForbiddenException("System Administrators do not have access to maintenance operations");
        }
    }

    private void validateEquipmentAccess(User user, Equipment equipment) {
        if (user.getRole() == User.Role.LAB_TECHNICIAN) {
            // Lab Technicians can access history if belonging to the institution
            Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
            Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
            if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
                throw new ForbiddenException("Access denied to equipment from another institution");
            }
            return;
        }
        if (user.getRole() == User.Role.LAB_MANAGER || user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR) {
            Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
            Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
            if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
                throw new ForbiddenException("Access denied to equipment from another institution");
            }
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
