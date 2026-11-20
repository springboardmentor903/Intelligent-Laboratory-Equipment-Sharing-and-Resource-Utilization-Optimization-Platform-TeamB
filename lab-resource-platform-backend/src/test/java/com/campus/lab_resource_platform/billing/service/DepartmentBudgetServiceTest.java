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
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DepartmentBudgetServiceTest {

    @Mock
    private DepartmentBudgetRepository budgetRepository;

    @Mock
    private UsageChargeRepository chargeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DepartmentBudgetService budgetService;

    private User admin;
    private User regularUser;
    private Department department;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@inst.edu");
        admin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        admin.setInstitutionId(10L);

        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setEmail("student@inst.edu");
        regularUser.setRole(User.Role.RESEARCHER_STUDENT);
        regularUser.setInstitutionId(10L);

        department = new Department();
        department.setId(5L);
        department.setName("Computer Science");
        department.setInstitutionId(10L);
    }

    @Test
    void createBudget_NonAdmin_ThrowsForbidden() {
        when(userRepository.findByEmail(regularUser.getEmail())).thenReturn(Optional.of(regularUser));

        DepartmentBudgetRequest req = new DepartmentBudgetRequest();
        req.setDepartmentId(5L);
        req.setFiscalYear(2026);
        req.setAllocatedAmount(BigDecimal.valueOf(10000.0));

        assertThrows(ForbiddenException.class, () ->
                budgetService.createBudget(req, regularUser.getEmail()));
    }

    @Test
    void createBudget_DuplicateForFiscalYear_ThrowsBadRequest() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(budgetRepository.existsByDepartmentIdAndFiscalYear(5L, 2026)).thenReturn(true);

        DepartmentBudgetRequest req = new DepartmentBudgetRequest();
        req.setDepartmentId(5L);
        req.setFiscalYear(2026);
        req.setAllocatedAmount(BigDecimal.valueOf(10000.0));

        assertThrows(BadRequestException.class, () ->
                budgetService.createBudget(req, admin.getEmail()));
    }

    @Test
    void createBudget_Success() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(budgetRepository.existsByDepartmentIdAndFiscalYear(5L, 2026)).thenReturn(false);
        when(departmentRepository.findById(5L)).thenReturn(Optional.of(department));
        when(budgetRepository.save(any(DepartmentBudget.class))).thenAnswer(i -> {
            DepartmentBudget b = i.getArgument(0);
            b.setId(40L);
            return b;
        });

        DepartmentBudgetRequest req = new DepartmentBudgetRequest();
        req.setDepartmentId(5L);
        req.setFiscalYear(2026);
        req.setAllocatedAmount(BigDecimal.valueOf(10000.0));

        DepartmentBudgetResponse res = budgetService.createBudget(req, admin.getEmail());

        assertNotNull(res);
        assertEquals(40L, res.getId());
        assertEquals(2026, res.getFiscalYear());
        assertEquals(BigDecimal.valueOf(10000.0), res.getAllocatedAmount());
    }

    @Test
    void getBudgets_CalculatesStatus_WarningAndExceeded() {
        DepartmentBudget budgetWarning = DepartmentBudget.builder()
                .id(1L)
                .department(department)
                .fiscalYear(2026)
                .allocatedAmount(BigDecimal.valueOf(1000.0))
                .build();

        UsageCharge charge850 = UsageCharge.builder()
                .departmentId(5L)
                .totalCharge(BigDecimal.valueOf(850.0))
                .build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(budgetRepository.findByInstitutionWithFilters(eq(10L), any(), any()))
                .thenReturn(List.of(budgetWarning));
        when(chargeRepository.findByDepartmentInPeriod(eq(5L), any(), any()))
                .thenReturn(List.of(charge850));

        List<DepartmentBudgetResponse> responses = budgetService.getBudgets(2026, null, admin.getEmail());

        assertEquals(1, responses.size());
        DepartmentBudgetResponse res = responses.get(0);
        assertEquals(BigDecimal.valueOf(850.0), res.getSpentAmount());
        assertEquals(BigDecimal.valueOf(150.0), res.getRemainingAmount());
        assertEquals(85.0, res.getUtilizationPercentage());
        assertEquals(BudgetStatus.WARNING, res.getStatus());
    }
}
