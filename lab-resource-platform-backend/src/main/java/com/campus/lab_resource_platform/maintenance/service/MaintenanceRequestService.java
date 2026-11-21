package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestCreateDto;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestResponse;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestReviewDto;
import com.campus.lab_resource_platform.maintenance.dto.WorkOrderCreateDto;
import com.campus.lab_resource_platform.maintenance.entity.*;
import com.campus.lab_resource_platform.maintenance.repository.MaintenanceRequestRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Service
public class MaintenanceRequestService {

    private static final Set<User.Role> ALLOWED_SUBMITTER_ROLES = Set.of(
            User.Role.RESEARCHER_STUDENT,
            User.Role.LAB_TECHNICIAN,
            User.Role.LAB_MANAGER,
            User.Role.DEPARTMENT_HEAD,
            User.Role.INSTITUTION_ADMINISTRATOR
    );

    private final MaintenanceRequestRepository requestRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final WorkOrderService workOrderService;

    public MaintenanceRequestService(
            MaintenanceRequestRepository requestRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            @Lazy WorkOrderService workOrderService) {
        this.requestRepository = requestRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.workOrderService = workOrderService;
    }

    @Transactional
    public MaintenanceRequestResponse submitRequest(MaintenanceRequestCreateDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        if (!ALLOWED_SUBMITTER_ROLES.contains(user.getRole())) {
            throw new ForbiddenException("Role " + user.getRole() + " is not permitted to submit maintenance requests");
        }

        Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + dto.getEquipmentId()));

        // Ensure user belongs to same institution or college as equipment
        Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
        if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
            throw new ForbiddenException("Cannot request maintenance for equipment belonging to another institution");
        }

        MaintenanceRequest request = MaintenanceRequest.builder()
                .equipment(equipment)
                .requester(user)
                .title(dto.getTitle().trim())
                .description(dto.getDescription().trim())
                .priority(dto.getPriority() != null ? dto.getPriority() : MaintenancePriority.MEDIUM)
                .status(MaintenanceRequestStatus.PENDING)
                .build();

        MaintenanceRequest saved = requestRepository.save(request);
        return MaintenanceRequestResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRequestResponse> getMyRequests(String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(MaintenanceRequestResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRequestResponse> getManagerQueue(
            MaintenanceRequestStatus status,
            MaintenancePriority priority,
            Long equipmentId,
            String userEmail,
            Pageable pageable) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        // Lab Manager or Institution Admin can view the queue
        if (user.getRole() != User.Role.LAB_MANAGER && user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR) {
            throw new ForbiddenException("Only Lab Managers and Institution Administrators can view the maintenance request queue");
        }

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L; // fallback default
        }

        return requestRepository.findByInstitutionWithFilters(instId, status, priority, equipmentId, pageable)
                .map(MaintenanceRequestResponse::fromEntity);
    }

    @Transactional
    public MaintenanceRequestResponse approveRequest(Long requestId, MaintenanceRequestReviewDto reviewDto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        MaintenanceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found with ID: " + requestId));

        // Only Lab Manager of the equipment's institution can approve
        validateLabManagerOfEquipment(user, request.getEquipment());

        if (request.getStatus() != MaintenanceRequestStatus.PENDING) {
            throw new BadRequestException("Request #" + requestId + " is already " + request.getStatus());
        }

        request.setStatus(MaintenanceRequestStatus.APPROVED);
        request.setReviewedBy(user);
        request.setReviewedAt(Instant.now());
        if (reviewDto != null && reviewDto.getRemarks() != null) {
            request.setRemarks(reviewDto.getRemarks().trim());
        }

        MaintenanceRequest saved = requestRepository.save(request);

        // Automatically create a CORRECTIVE work order when a request is approved
        WorkOrderCreateDto woDto = WorkOrderCreateDto.builder()
                .equipmentId(request.getEquipment().getId())
                .type(WorkOrderType.CORRECTIVE)
                .title("Corrective Maintenance: " + request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .maintenanceRequestId(request.getId())
                .build();

        workOrderService.createWorkOrderInternal(woDto, user);

        return MaintenanceRequestResponse.fromEntity(saved);
    }

    @Transactional
    public MaintenanceRequestResponse rejectRequest(Long requestId, MaintenanceRequestReviewDto reviewDto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        MaintenanceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found with ID: " + requestId));

        // Only Lab Manager of the equipment's institution can reject
        validateLabManagerOfEquipment(user, request.getEquipment());

        if (request.getStatus() != MaintenanceRequestStatus.PENDING) {
            throw new BadRequestException("Request #" + requestId + " is already " + request.getStatus());
        }

        request.setStatus(MaintenanceRequestStatus.REJECTED);
        request.setReviewedBy(user);
        request.setReviewedAt(Instant.now());
        if (reviewDto != null && reviewDto.getRemarks() != null) {
            request.setRemarks(reviewDto.getRemarks().trim());
        }

        MaintenanceRequest saved = requestRepository.save(request);
        return MaintenanceRequestResponse.fromEntity(saved);
    }

    private void validateNotSystemAdmin(User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ForbiddenException("System Administrators do not have access to maintenance operations");
        }
    }

    private void validateLabManagerOfEquipment(User user, Equipment equipment) {
        if (user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only the Lab Manager can review and approve/reject maintenance requests");
        }
        Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
        if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
            throw new ForbiddenException("Only the Lab Manager of the equipment's institution can review this request");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
