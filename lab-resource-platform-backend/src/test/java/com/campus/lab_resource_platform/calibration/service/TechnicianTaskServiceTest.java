package com.campus.lab_resource_platform.calibration.service;

import com.campus.lab_resource_platform.calibration.dto.TechnicianTaskDto;
import com.campus.lab_resource_platform.calibration.dto.TechnicianWorkloadDto;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
public class TechnicianTaskServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TechnicianTaskService technicianTaskService;

    private User technician;
    private User manager;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        technician = new User();
        technician.setId(2L);
        technician.setEmail("tech@campus.edu");
        technician.setRole(User.Role.LAB_TECHNICIAN);
        technician.setInstitutionId(10L);

        manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@campus.edu");
        manager.setRole(User.Role.LAB_MANAGER);
        manager.setInstitutionId(10L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Gas Chromatograph");
        equipment.setInstitutionId(10L);
    }

    @Test
    void getMyTasks_ReturnsSummaryCountsAndPagedList() {
        WorkOrder wo = WorkOrder.builder()
                .id(10L)
                .workOrderNumber("WO-2026-000010")
                .status(WorkOrderStatus.ASSIGNED)
                .priority(MaintenancePriority.HIGH)
                .assignedTechnician(technician)
                .equipment(equipment)
                .expectedEndDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(workOrderRepository.findByTechnicianWithFilters(eq(technician.getId()), any(), any()))
                .thenReturn(new PageImpl<>(List.of(wo)));

        TechnicianTaskDto overview = technicianTaskService.getMyTasks(
                null, technician.getEmail(), PageRequest.of(0, 10));

        assertNotNull(overview);
        assertEquals(1, overview.getCountsByStatus().get("ASSIGNED"));
        assertEquals(1, overview.getOverdueTasks());
        assertEquals(1, overview.getTotalTasks());
        assertEquals(1, overview.getTasks().size());
    }

    @Test
    void getTechnicianWorkload_UsesGroupedCounts() {
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findAll()).thenReturn(List.of(technician));

        // Grouped row: [technicianId, WorkOrderStatus, count]
        Object[] row1 = new Object[]{2L, WorkOrderStatus.ASSIGNED, 2L};
        Object[] row2 = new Object[]{2L, WorkOrderStatus.IN_PROGRESS, 1L};
        when(workOrderRepository.findTechnicianWorkloadGrouped(10L)).thenReturn(List.of(row1, row2));
        when(workOrderRepository.findUpcomingForTechnician(eq(2L), any())).thenReturn(java.util.Collections.emptyList());
        when(workOrderRepository.findByTechnicianWithFilters(eq(2L), isNull(), any()))
                .thenReturn(new PageImpl<>(java.util.Collections.emptyList()));

        List<TechnicianWorkloadDto> workload = technicianTaskService.getTechnicianWorkload(manager.getEmail());

        assertEquals(1, workload.size());
        TechnicianWorkloadDto dto = workload.get(0);
        assertEquals(2L, dto.getTechnicianId());
        assertEquals(3, dto.getActiveTaskCount());
        assertEquals(1, dto.getInProgressTaskCount());
    }
}
