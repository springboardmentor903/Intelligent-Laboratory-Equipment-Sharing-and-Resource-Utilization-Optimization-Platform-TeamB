package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.DowntimeSummaryResponse;
import com.campus.lab_resource_platform.maintenance.dto.EquipmentMaintenanceHistoryResponse;
import com.campus.lab_resource_platform.maintenance.entity.*;
import com.campus.lab_resource_platform.maintenance.repository.EquipmentDowntimeRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DowntimeAnalyticsServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private EquipmentDowntimeRepository downtimeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DowntimeAnalyticsService analyticsService;

    private User manager;
    private Equipment equipment;
    private Department department;

    @BeforeEach
    void setUp() {
        manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@lab.edu");
        manager.setRole(User.Role.LAB_MANAGER);
        manager.setInstitutionId(10L);

        department = new Department();
        department.setId(5L);
        department.setName("Physics");
        department.setInstitutionId(10L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("NMR Spectrometer");
        equipment.setInstitutionId(10L);
        equipment.setDepartmentId(5L);
    }

    @Test
    void getEquipmentMaintenanceHistory_ReturnsCompletedRecords() {
        WorkOrder wo = WorkOrder.builder()
                .id(1L)
                .workOrderNumber("WO-2026-000001")
                .type(WorkOrderType.CORRECTIVE)
                .status(WorkOrderStatus.COMPLETED)
                .equipment(equipment)
                .cost(BigDecimal.valueOf(150.0))
                .resolutionNotes("Replaced fuse")
                .actualStartDate(Instant.now().minus(4, ChronoUnit.HOURS))
                .actualEndDate(Instant.now())
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.findByEquipmentIdAndStatusOrderByActualEndDateDesc(100L, WorkOrderStatus.COMPLETED))
                .thenReturn(List.of(wo));

        EquipmentMaintenanceHistoryResponse history =
                analyticsService.getEquipmentMaintenanceHistory(100L, manager.getEmail());

        assertNotNull(history);
        assertEquals(1, history.getTotalWorkOrdersCompleted());
        assertEquals(1, history.getHistoryItems().size());
        assertEquals("WO-2026-000001", history.getHistoryItems().get(0).getWorkOrderNumber());
    }

    @Test
    void getDowntimeSummary_CalculatesMetrics() {
        Instant now = Instant.now();

        EquipmentDowntime dt = EquipmentDowntime.builder()
                .id(1L)
                .equipment(equipment)
                .startTime(now.minus(2, ChronoUnit.HOURS))
                .endTime(now)
                .totalDowntimeMinutes(120L)
                .openHoursDowntimeMinutes(60L)
                .build();

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(downtimeRepository.findDowntimesInRange(eq(10L), isNull(), eq(100L), any()))
                .thenReturn(List.of(dt));

        DowntimeSummaryResponse summary = analyticsService.getDowntimeSummary("EQUIPMENT", 100L, "30d", manager.getEmail());

        assertNotNull(summary);
        assertEquals(120L, summary.getTotalRawDowntimeMinutes());
        assertEquals(60L, summary.getOpenHoursDowntimeMinutes());
        assertEquals(1, summary.getEquipmentDowntimes().size());
    }
}
