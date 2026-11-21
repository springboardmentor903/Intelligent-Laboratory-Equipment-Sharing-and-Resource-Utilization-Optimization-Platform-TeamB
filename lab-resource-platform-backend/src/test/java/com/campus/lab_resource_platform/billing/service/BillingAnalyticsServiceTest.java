package com.campus.lab_resource_platform.billing.service;

import com.campus.lab_resource_platform.billing.dto.DepartmentAllocationResponse;
import com.campus.lab_resource_platform.billing.dto.EquipmentCostAnalyticsResponse;
import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import com.campus.lab_resource_platform.billing.repository.UsageChargeRepository;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BillingAnalyticsServiceTest {

    @Mock
    private UsageChargeRepository chargeRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BillingAnalyticsService billingAnalyticsService;

    private User admin;
    private Department dept1;
    private Equipment eq1;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@inst.edu");
        admin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        admin.setInstitutionId(10L);

        dept1 = new Department();
        dept1.setId(5L);
        dept1.setName("Bioengineering");
        dept1.setInstitutionId(10L);

        eq1 = new Equipment();
        eq1.setId(100L);
        eq1.setName("Flow Cytometer");
        eq1.setInstitutionId(10L);
        eq1.setDepartmentId(5L);
    }

    @Test
    void getEquipmentCostAnalytics_SeparatesUsageAndMaintenanceCost() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(equipmentRepository.findByInstitutionIdOrCollegeId(10L, 10L)).thenReturn(List.of(eq1));

        UsageCharge charge = UsageCharge.builder()
                .equipment(eq1)
                .chargeType(ChargeType.INTERNAL)
                .usedMinutes(120)
                .totalCharge(BigDecimal.valueOf(150.0))
                .createdAt(Instant.now())
                .build();

        when(chargeRepository.findAllByInstitutionInPeriod(eq(10L), any(), any()))
                .thenReturn(List.of(charge));

        WorkOrder wo = WorkOrder.builder()
                .equipment(eq1)
                .cost(BigDecimal.valueOf(300.0))
                .actualEndDate(Instant.now())
                .build();

        when(workOrderRepository.findByEquipmentIdAndStatusOrderByActualEndDateDesc(100L, WorkOrderStatus.COMPLETED))
                .thenReturn(List.of(wo));

        EquipmentCostAnalyticsResponse response =
                billingAnalyticsService.getEquipmentCostAnalytics("monthly", admin.getEmail());

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(150.0), response.getGrandTotalUsageCost());
        assertEquals(BigDecimal.valueOf(300.0), response.getGrandTotalMaintenanceCost());
        assertEquals(1, response.getItems().size());
        assertEquals("Flow Cytometer", response.getItems().get(0).getEquipmentName());
    }

    @Test
    void getDepartmentAllocation_CalculatesPercentageShares() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(departmentRepository.findAll()).thenReturn(List.of(dept1));

        UsageCharge internalCharge = UsageCharge.builder()
                .departmentId(5L)
                .chargeType(ChargeType.INTERNAL)
                .totalCharge(BigDecimal.valueOf(200.0))
                .build();

        UsageCharge externalCharge = UsageCharge.builder()
                .departmentId(5L)
                .chargeType(ChargeType.EXTERNAL)
                .totalCharge(BigDecimal.valueOf(50.0))
                .build();

        when(chargeRepository.findAllByInstitutionInPeriod(eq(10L), any(), any()))
                .thenReturn(List.of(internalCharge, externalCharge));

        DepartmentAllocationResponse alloc =
                billingAnalyticsService.getDepartmentAllocationAnalytics("annual", null, admin.getEmail());

        assertNotNull(alloc);
        assertEquals(BigDecimal.valueOf(250.0), alloc.getGrandTotalCost());
        assertEquals(1, alloc.getDepartments().size());
        assertEquals(100.0, alloc.getDepartments().get(0).getPercentageShare());
    }
}
