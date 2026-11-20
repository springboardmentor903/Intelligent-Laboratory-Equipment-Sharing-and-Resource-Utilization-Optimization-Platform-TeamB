package com.campus.lab_resource_platform.billing.service;

import com.campus.lab_resource_platform.billing.dto.DepartmentBudgetRequest;
import com.campus.lab_resource_platform.billing.dto.DepartmentBudgetResponse;
import com.campus.lab_resource_platform.billing.entity.BudgetStatus;
import com.campus.lab_resource_platform.billing.entity.DepartmentBudget;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import com.campus.lab_resource_platform.billing.repository.DepartmentBudgetRepository;
import com.campus.lab_resource_platform.billing.repository.UsageChargeRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DepartmentBudgetService {

    private final DepartmentBudgetRepository budgetRepository;
    private final UsageChargeRepository chargeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @Value("${app.fiscal-year.start-month:1}")
    private int fiscalYearStartMonth;

    public DepartmentBudgetService(
            DepartmentBudgetRepository budgetRepository,
            UsageChargeRepository chargeRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.chargeRepository = chargeRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DepartmentBudgetResponse createBudget(DepartmentBudgetRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateInstitutionAdmin(user);

        if (budgetRepository.existsByDepartmentIdAndFiscalYear(request.getDepartmentId(), request.getFiscalYear())) {
            throw new BadRequestException("Budget already exists for department ID " +
                    request.getDepartmentId() + " in fiscal year " + request.getFiscalYear());
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + request.getDepartmentId()));

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        DepartmentBudget budget = DepartmentBudget.builder()
                .institutionId(instId)
                .department(department)
                .fiscalYear(request.getFiscalYear())
                .allocatedAmount(request.getAllocatedAmount())
                .build();

        DepartmentBudget saved = budgetRepository.save(budget);
        return mapToResponse(saved);
    }

    @Transactional
    public DepartmentBudgetResponse updateBudget(Long id, DepartmentBudgetRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateInstitutionAdmin(user);

        DepartmentBudget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found with ID: " + id));

        if (!budget.getDepartment().getId().equals(request.getDepartmentId()) || !budget.getFiscalYear().equals(request.getFiscalYear())) {
            if (budgetRepository.existsByDepartmentIdAndFiscalYear(request.getDepartmentId(), request.getFiscalYear())) {
                throw new BadRequestException("Another budget already exists for department ID " +
                        request.getDepartmentId() + " and fiscal year " + request.getFiscalYear());
            }
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + request.getDepartmentId()));

        budget.setDepartment(department);
        budget.setFiscalYear(request.getFiscalYear());
        budget.setAllocatedAmount(request.getAllocatedAmount());

        DepartmentBudget saved = budgetRepository.save(budget);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DepartmentBudgetResponse> getBudgets(Integer fiscalYear, Long departmentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateStaffAccess(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        List<DepartmentBudget> budgets = budgetRepository.findByInstitutionWithFilters(instId, fiscalYear, departmentId);
        List<DepartmentBudgetResponse> responses = new ArrayList<>();

        for (DepartmentBudget b : budgets) {
            responses.add(mapToResponse(b));
        }

        return responses;
    }

    private DepartmentBudgetResponse mapToResponse(DepartmentBudget b) {
        // Calculate fiscal year date range
        int startMonth = fiscalYearStartMonth > 0 && fiscalYearStartMonth <= 12 ? fiscalYearStartMonth : 1;
        LocalDate fiscalStart = LocalDate.of(b.getFiscalYear(), startMonth, 1);
        LocalDate fiscalEnd = fiscalStart.plusYears(1).minusDays(1);

        Instant startInstant = fiscalStart.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant endInstant = fiscalEnd.atTime(23, 59, 59).atZone(ZoneId.of("Asia/Kolkata")).toInstant();

        List<UsageCharge> charges = chargeRepository.findByDepartmentInPeriod(b.getDepartment().getId(), startInstant, endInstant);
        BigDecimal spent = charges.stream()
                .map(UsageCharge::getTotalCharge)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal allocated = b.getAllocatedAmount() != null ? b.getAllocatedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = allocated.subtract(spent);

        double utilPct = 0.0;
        BudgetStatus status;

        if (allocated.compareTo(BigDecimal.ZERO) <= 0) {
            status = BudgetStatus.NO_BUDGET;
        } else {
            utilPct = spent.multiply(BigDecimal.valueOf(100)).divide(allocated, 2, RoundingMode.HALF_UP).doubleValue();
            if (utilPct > 100.0) {
                status = BudgetStatus.EXCEEDED;
            } else if (utilPct >= 80.0) {
                status = BudgetStatus.WARNING;
            } else {
                status = BudgetStatus.OK;
            }
        }

        return DepartmentBudgetResponse.builder()
                .id(b.getId())
                .institutionId(b.getInstitutionId())
                .departmentId(b.getDepartment().getId())
                .departmentName(b.getDepartment().getName())
                .fiscalYear(b.getFiscalYear())
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .remainingAmount(remaining)
                .utilizationPercentage(utilPct)
                .status(status)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private void validateInstitutionAdmin(User user) {
        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR) {
            throw new ForbiddenException("Only the Institution Administrator can create or update department budgets");
        }
    }

    private void validateStaffAccess(User user) {
        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR
                && user.getRole() != User.Role.DEPARTMENT_HEAD
                && user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only Institution Administrators, Department Heads, and Lab Managers can access budgets");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
