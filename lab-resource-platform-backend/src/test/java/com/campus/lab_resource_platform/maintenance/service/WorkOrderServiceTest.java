package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.repository.WaitlistRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.*;
import com.campus.lab_resource_platform.maintenance.entity.*;
import com.campus.lab_resource_platform.maintenance.repository.EquipmentDowntimeRepository;
import com.campus.lab_resource_platform.maintenance.repository.MaintenanceRequestRepository;
import com.campus.lab_resource_platform.maintenance.repository.PreventiveScheduleRepository;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderServiceLogRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private WorkOrderServiceLogRepository serviceLogRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private MaintenanceRequestRepository requestRepository;

    @Mock
    private PreventiveScheduleRepository scheduleRepository;

    @Mock
    private EquipmentDowntimeRepository downtimeRepository;

    @Mock
    private CalibrationRecordRepository calibrationRecordRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingHistoryRepository bookingHistoryRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WorkOrderService workOrderService;

    private User manager;
    private User technician;
    private User otherTech;
    private Equipment equipment;
    private WorkOrder workOrder;

    private User createUser(Long id, String email, User.Role role, Long instId) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        u.setInstitutionId(instId);
        return u;
    }

    @BeforeEach
    void setUp() {
        manager = createUser(1L, "manager@lab.edu", User.Role.LAB_MANAGER, 10L);
        technician = createUser(2L, "tech@lab.edu", User.Role.LAB_TECHNICIAN, 10L);
        otherTech = createUser(3L, "other_tech@lab.edu", User.Role.LAB_TECHNICIAN, 99L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Electron Microscope");
        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setInstitutionId(10L);

        workOrder = WorkOrder.builder()
                .id(1L)
                .workOrderNumber("WO-2026-000001")
                .type(WorkOrderType.CORRECTIVE)
                .status(WorkOrderStatus.OPEN)
                .equipment(equipment)
                .build();
    }

    @Test
    void generateNextWorkOrderNumber_FormatsCorrectly() {
        when(workOrderRepository.findMaxWorkOrderNumberWithPrefix(anyString())).thenReturn("WO-2026-000042");
        String next = workOrderService.generateNextWorkOrderNumber();
        assertEquals("WO-2026-000043", next);
    }

    @Test
    void assignTechnician_SameInstitution_Success() {
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));
        when(userRepository.findById(technician.getId())).thenReturn(Optional.of(technician));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

        WorkOrderAssignDto assignDto = new WorkOrderAssignDto();
        assignDto.setTechnicianId(technician.getId());
        assignDto.setNotes("Assigned to primary technician");

        WorkOrderResponse response = workOrderService.assignTechnician(1L, assignDto, manager.getEmail());

        assertEquals(WorkOrderStatus.ASSIGNED, response.getStatus());
        assertEquals(technician.getId(), response.getAssignedTechnicianId());
        verify(serviceLogRepository).save(any(WorkOrderServiceLog.class));
    }

    @Test
    void assignTechnician_DifferentInstitution_ThrowsBadRequest() {
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));
        when(userRepository.findById(otherTech.getId())).thenReturn(Optional.of(otherTech));

        WorkOrderAssignDto assignDto = new WorkOrderAssignDto();
        assignDto.setTechnicianId(otherTech.getId());

        assertThrows(BadRequestException.class, () ->
                workOrderService.assignTechnician(1L, assignDto, manager.getEmail()));
    }

    @Test
    void startWorkOrder_CancelsOverlappingBookingsAndUpdatesEquipmentStatus() {
        workOrder.setStatus(WorkOrderStatus.ASSIGNED);
        workOrder.setAssignedTechnician(technician);

        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);
        workOrder.setExpectedEndDate(end);

        Booking booking = Booking.builder()
                .id(50L)
                .equipment(equipment)
                .user(manager)
                .startTime(start.plus(30, ChronoUnit.MINUTES))
                .endTime(start.plus(90, ChronoUnit.MINUTES))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));
        when(bookingRepository.findConflictingBookings(eq(equipment.getId()), any(), any(), any()))
                .thenReturn(List.of(booking));
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

        WorkOrderResponse response = workOrderService.startWorkOrder(1L, technician.getEmail());

        assertEquals(WorkOrderStatus.IN_PROGRESS, response.getStatus());
        assertEquals(EquipmentStatus.UNDER_MAINTENANCE, equipment.getStatus());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("Equipment under maintenance", booking.getCancellationReason());
        verify(bookingRepository).save(booking);
        verify(bookingHistoryRepository).save(any());
        verify(downtimeRepository).save(any());
    }

    @Test
    void completeWorkOrder_RestoresEquipmentStatusWhenNoOtherInProgress() {
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrder.setAssignedTechnician(technician);
        workOrder.setActualStartDate(Instant.now().minus(2, ChronoUnit.HOURS));

        EquipmentDowntime downtime = EquipmentDowntime.builder()
                .equipment(equipment)
                .workOrder(workOrder)
                .startTime(workOrder.getActualStartDate())
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));
        when(downtimeRepository.findFirstByWorkOrderIdOrderByStartTimeDesc(1L)).thenReturn(Optional.of(downtime));
        when(workOrderRepository.existsOtherInProgressWorkOrder(equipment.getId(), 1L)).thenReturn(false);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

        WorkOrderCompleteDto completeDto = new WorkOrderCompleteDto();
        completeDto.setResolutionNotes("Lens cleaned and recalibrated");

        WorkOrderResponse response = workOrderService.completeWorkOrder(1L, completeDto, technician.getEmail());

        assertEquals(WorkOrderStatus.COMPLETED, response.getStatus());
        assertEquals(EquipmentStatus.AVAILABLE, equipment.getStatus());
        assertNotNull(downtime.getEndTime());
        assertTrue(downtime.getTotalDowntimeMinutes() > 0);
    }
}
