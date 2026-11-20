package com.campus.lab_resource_platform.billing.service;

import com.campus.lab_resource_platform.billing.dto.DepartmentAllocationResponse;
import com.campus.lab_resource_platform.billing.dto.EquipmentCostAnalyticsResponse;
import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import com.campus.lab_resource_platform.billing.repository.UsageChargeRepository;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillingAnalyticsService {

    private final UsageChargeRepository chargeRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;

    public BillingAnalyticsService(
            UsageChargeRepository chargeRepository,
            EquipmentRepository equipmentRepository,
            DepartmentRepository departmentRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository) {
        this.chargeRepository = chargeRepository;
        this.equipmentRepository = equipmentRepository;
        this.departmentRepository = departmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public EquipmentCostAnalyticsResponse getEquipmentCostAnalytics(String period, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateStaffAccess(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        Instant startDate = resolvePeriodStartDate(period);
        Instant endDate = Instant.now();

        List<UsageCharge> charges = chargeRepository.findAllByInstitutionInPeriod(instId, startDate, endDate);
        List<Equipment> equipmentList = equipmentRepository.findByInstitutionIdOrCollegeId(instId, instId);

        Map<Long, List<UsageCharge>> chargesByEquipment = charges.stream()
                .collect(Collectors.groupingBy(c -> c.getEquipment().getId()));

        double grandTotalHours = 0.0;
        BigDecimal grandInternal = BigDecimal.ZERO;
        BigDecimal grandExternal = BigDecimal.ZERO;
        BigDecimal grandUnassigned = BigDecimal.ZERO;
        BigDecimal grandUsage = BigDecimal.ZERO;
        BigDecimal grandMaintenance = BigDecimal.ZERO;

        List<EquipmentCostAnalyticsResponse.EquipmentCostItemDto> items = new ArrayList<>();

        for (Equipment eq : equipmentList) {
            List<UsageCharge> eqCharges = chargesByEquipment.getOrDefault(eq.getId(), Collections.emptyList());

            int totalMinutes = eqCharges.stream().mapToInt(UsageCharge::getUsedMinutes).sum();
            double hours = totalMinutes / 60.0;

            BigDecimal internalCost = BigDecimal.ZERO;
            BigDecimal externalCost = BigDecimal.ZERO;
            BigDecimal unassignedCost = BigDecimal.ZERO;
            BigDecimal totalUsageCost = BigDecimal.ZERO;

            for (UsageCharge c : eqCharges) {
                BigDecimal amt = c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO;
                totalUsageCost = totalUsageCost.add(amt);
                if (c.getChargeType() == ChargeType.INTERNAL) {
                    internalCost = internalCost.add(amt);
                } else if (c.getChargeType() == ChargeType.EXTERNAL) {
                    externalCost = externalCost.add(amt);
                } else if (c.getChargeType() == ChargeType.UNASSIGNED) {
                    unassignedCost = unassignedCost.add(amt);
                }
            }

            // Maintenance cost kept separate from usage costs
            List<WorkOrder> completedWo = workOrderRepository.findByEquipmentIdAndStatusOrderByActualEndDateDesc(
                    eq.getId(), WorkOrderStatus.COMPLETED);
            BigDecimal maintCost = completedWo.stream()
                    .map(WorkOrder::getCost)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            grandTotalHours += hours;
            grandInternal = grandInternal.add(internalCost);
            grandExternal = grandExternal.add(externalCost);
            grandUnassigned = grandUnassigned.add(unassignedCost);
            grandUsage = grandUsage.add(totalUsageCost);
            grandMaintenance = grandMaintenance.add(maintCost);

            String deptName = null;
            if (eq.getDepartmentId() != null) {
                deptName = departmentRepository.findById(eq.getDepartmentId())
                        .map(Department::getName).orElse("Department " + eq.getDepartmentId());
            }

            items.add(EquipmentCostAnalyticsResponse.EquipmentCostItemDto.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .departmentId(eq.getDepartmentId())
                    .departmentName(deptName)
                    .usageHours(Math.round(hours * 10.0) / 10.0)
                    .internalCost(internalCost)
                    .externalCost(externalCost)
                    .unassignedCost(unassignedCost)
                    .totalUsageCost(totalUsageCost)
                    .maintenanceCost(maintCost)
                    .build());
        }

        return EquipmentCostAnalyticsResponse.builder()
                .period(period != null ? period : "LAST_30_DAYS")
                .institutionId(instId)
                .grandTotalUsageHours(Math.round(grandTotalHours * 10.0) / 10.0)
                .grandTotalInternalCost(grandInternal)
                .grandTotalExternalCost(grandExternal)
                .grandTotalUnassignedCost(grandUnassigned)
                .grandTotalUsageCost(grandUsage)
                .grandTotalMaintenanceCost(grandMaintenance)
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public DepartmentAllocationResponse getDepartmentAllocationAnalytics(String period, Long departmentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateStaffAccess(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        Instant startDate = resolvePeriodStartDate(period);
        Instant endDate = Instant.now();

        List<UsageCharge> charges = chargeRepository.findAllByInstitutionInPeriod(instId, startDate, endDate);
        List<Department> departments = departmentRepository.findAll();

        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal grandInternal = BigDecimal.ZERO;
        BigDecimal grandExternal = BigDecimal.ZERO;

        Map<Long, List<UsageCharge>> byDept = new HashMap<>();
        List<UsageCharge> unassignedList = new ArrayList<>();

        for (UsageCharge c : charges) {
            BigDecimal amt = c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO;
            grandTotal = grandTotal.add(amt);
            if (c.getChargeType() == ChargeType.INTERNAL) {
                grandInternal = grandInternal.add(amt);
            } else if (c.getChargeType() == ChargeType.EXTERNAL) {
                grandExternal = grandExternal.add(amt);
            }

            if (c.getDepartmentId() != null) {
                byDept.computeIfAbsent(c.getDepartmentId(), k -> new ArrayList<>()).add(c);
            } else {
                unassignedList.add(c);
            }
        }

        List<DepartmentAllocationResponse.DepartmentAllocationItemDto> deptDtos = new ArrayList<>();

        for (Department d : departments) {
            if (departmentId != null && !Objects.equals(d.getId(), departmentId)) {
                continue;
            }

            List<UsageCharge> dCharges = byDept.getOrDefault(d.getId(), Collections.emptyList());
            BigDecimal dInternal = BigDecimal.ZERO;
            BigDecimal dExternal = BigDecimal.ZERO;
            BigDecimal dTotal = BigDecimal.ZERO;

            for (UsageCharge c : dCharges) {
                BigDecimal amt = c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO;
                dTotal = dTotal.add(amt);
                if (c.getChargeType() == ChargeType.INTERNAL) {
                    dInternal = dInternal.add(amt);
                } else if (c.getChargeType() == ChargeType.EXTERNAL) {
                    dExternal = dExternal.add(amt);
                }
            }

            double pct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                    ? dTotal.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            deptDtos.add(DepartmentAllocationResponse.DepartmentAllocationItemDto.builder()
                    .departmentId(d.getId())
                    .departmentName(d.getName())
                    .internalCost(dInternal)
                    .externalCost(dExternal)
                    .totalCost(dTotal)
                    .percentageShare(pct)
                    .sessionsCount(dCharges.size())
                    .build());
        }

        // Unallocated charges for users without a department
        BigDecimal unallocInternal = BigDecimal.ZERO;
        BigDecimal unallocExternal = BigDecimal.ZERO;
        BigDecimal unallocTotal = BigDecimal.ZERO;

        for (UsageCharge c : unassignedList) {
            BigDecimal amt = c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO;
            unallocTotal = unallocTotal.add(amt);
            if (c.getChargeType() == ChargeType.INTERNAL) {
                unallocInternal = unallocInternal.add(amt);
            } else if (c.getChargeType() == ChargeType.EXTERNAL) {
                unallocExternal = unallocExternal.add(amt);
            }
        }

        double unallocPct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                ? unallocTotal.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        DepartmentAllocationResponse.DepartmentAllocationItemDto unallocatedDto =
                DepartmentAllocationResponse.DepartmentAllocationItemDto.builder()
                        .departmentId(null)
                        .departmentName("Unallocated / General")
                        .internalCost(unallocInternal)
                        .externalCost(unallocExternal)
                        .totalCost(unallocTotal)
                        .percentageShare(unallocPct)
                        .sessionsCount(unassignedList.size())
                        .build();

        return DepartmentAllocationResponse.builder()
                .period(period != null ? period : "LAST_30_DAYS")
                .institutionId(instId)
                .grandTotalInternalCost(grandInternal)
                .grandTotalExternalCost(grandExternal)
                .grandTotalCost(grandTotal)
                .departments(deptDtos)
                .unallocated(unallocatedDto)
                .build();
    }

    private Instant resolvePeriodStartDate(String period) {
        if (period == null) return Instant.now().minus(Duration.ofDays(30));
        return switch (period.toUpperCase()) {
            case "LAST_7_DAYS", "7_DAYS" -> Instant.now().minus(Duration.ofDays(7));
            case "LAST_30_DAYS", "30_DAYS", "MONTH" -> Instant.now().minus(Duration.ofDays(30));
            case "LAST_90_DAYS", "90_DAYS", "QUARTER" -> Instant.now().minus(Duration.ofDays(90));
            case "LAST_YEAR", "YEAR" -> Instant.now().minus(Duration.ofDays(365));
            default -> Instant.now().minus(Duration.ofDays(30));
        };
    }

    private void validateStaffAccess(User user) {
        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR
                && user.getRole() != User.Role.DEPARTMENT_HEAD
                && user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only Institution Administrators, Department Heads, and Lab Managers can view billing analytics");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
