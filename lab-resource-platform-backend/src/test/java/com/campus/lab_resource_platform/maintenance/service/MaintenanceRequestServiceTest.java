package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestCreateDto;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestResponse;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestReviewDto;
import com.campus.lab_resource_platform.maintenance.dto.WorkOrderCreateDto;
import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequest;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequestStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.repository.MaintenanceRequestRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MaintenanceRequestServiceTest {

    @Mock
    private MaintenanceRequestRepository requestRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkOrderService workOrderService;

    @InjectMocks
    private MaintenanceRequestService requestService;

    private User student;
    private User labManager;
    private User otherManager;
    private Equipment equipment;

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
        student = createUser(1L, "student@campus.edu", User.Role.RESEARCHER_STUDENT, 10L);
        labManager = createUser(2L, "manager@campus.edu", User.Role.LAB_MANAGER, 10L);
        otherManager = createUser(3L, "other_manager@campus.edu", User.Role.LAB_MANAGER, 20L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Spectrophotometer");
        equipment.setInstitutionId(10L);
    }

    @Test
    void submitRequest_Success() {
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(requestRepository.save(any(MaintenanceRequest.class))).thenAnswer(invocation -> {
            MaintenanceRequest req = invocation.getArgument(0);
            req.setId(500L);
            return req;
        });

        MaintenanceRequestCreateDto dto = new MaintenanceRequestCreateDto();
        dto.setEquipmentId(100L);
        dto.setTitle("Beam alignment error");
        dto.setDescription("Laser not calibrated properly");
        dto.setPriority(MaintenancePriority.HIGH);

        MaintenanceRequestResponse response = requestService.submitRequest(dto, student.getEmail());

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals("Beam alignment error", response.getTitle());
        assertEquals(MaintenanceRequestStatus.PENDING, response.getStatus());
        assertEquals(MaintenancePriority.HIGH, response.getPriority());
    }

    @Test
    void approveRequest_CreatesCorrectiveWorkOrder() {
        MaintenanceRequest request = MaintenanceRequest.builder()
                .id(500L)
                .equipment(equipment)
                .requester(student)
                .title("Alignment error")
                .priority(MaintenancePriority.HIGH)
                .status(MaintenanceRequestStatus.PENDING)
                .build();

        when(userRepository.findByEmail(labManager.getEmail())).thenReturn(Optional.of(labManager));
        when(requestRepository.findById(500L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(MaintenanceRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkOrder mockWorkOrder = WorkOrder.builder()
                .id(900L)
                .workOrderNumber("WO-2026-000001")
                .build();
        when(workOrderService.createWorkOrderInternal(any(WorkOrderCreateDto.class), eq(labManager)))
                .thenReturn(mockWorkOrder);

        MaintenanceRequestReviewDto reviewDto = new MaintenanceRequestReviewDto();
        reviewDto.setRemarks("Approved for corrective maintenance");

        MaintenanceRequestResponse response = requestService.approveRequest(500L, reviewDto, labManager.getEmail());

        assertEquals(MaintenanceRequestStatus.APPROVED, response.getStatus());
        assertEquals("Approved for corrective maintenance", response.getRemarks());
        verify(workOrderService).createWorkOrderInternal(any(WorkOrderCreateDto.class), eq(labManager));
    }

    @Test
    void reviewRequest_DifferentInstitution_ThrowsForbidden() {
        MaintenanceRequest request = MaintenanceRequest.builder()
                .id(500L)
                .equipment(equipment)
                .requester(student)
                .status(MaintenanceRequestStatus.PENDING)
                .build();

        when(userRepository.findByEmail(otherManager.getEmail())).thenReturn(Optional.of(otherManager));
        when(requestRepository.findById(500L)).thenReturn(Optional.of(request));

        MaintenanceRequestReviewDto reviewDto = new MaintenanceRequestReviewDto();
        reviewDto.setRemarks("Not my lab");

        assertThrows(ForbiddenException.class, () ->
                requestService.rejectRequest(500L, reviewDto, otherManager.getEmail()));
    }

    @Test
    void getMyRequests_ReturnsPagedList() {
        MaintenanceRequest request = MaintenanceRequest.builder()
                .id(500L)
                .equipment(equipment)
                .requester(student)
                .status(MaintenanceRequestStatus.PENDING)
                .build();

        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(requestRepository.findByRequesterIdOrderByCreatedAtDesc(eq(student.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(request)));

        Page<MaintenanceRequestResponse> page = requestService.getMyRequests(student.getEmail(), PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(500L, page.getContent().get(0).getId());
    }
}
