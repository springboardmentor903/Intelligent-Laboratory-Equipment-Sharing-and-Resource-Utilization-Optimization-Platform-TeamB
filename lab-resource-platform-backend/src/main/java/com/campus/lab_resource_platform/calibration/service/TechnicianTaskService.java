package com.campus.lab_resource_platform.calibration.service;

import com.campus.lab_resource_platform.calibration.dto.TechnicianTaskDto;
import com.campus.lab_resource_platform.calibration.dto.TechnicianWorkloadDto;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.maintenance.dto.WorkOrderResponse;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TechnicianTaskService {

    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;

    public TechnicianTaskService(
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository) {
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public TechnicianTaskDto getMyTasks(WorkOrderStatus status, String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        if (user.getRole() != User.Role.LAB_TECHNICIAN && user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only Lab Technicians and Lab Managers can access technician tasks");
        }

        Page<WorkOrder> page = workOrderRepository.findByTechnicianWithFilters(user.getId(), status, pageable);
        List<WorkOrderResponse> taskDtos = page.getContent().stream()
                .map(WorkOrderResponse::fromEntity)
                .toList();

        // Calculate counts
        int overdueCount = 0;
        Map<String, Integer> counts = new HashMap<>();
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            counts.put(s.name(), 0);
        }

        List<WorkOrder> allMyOrders = workOrderRepository.findByTechnicianWithFilters(user.getId(), null, Pageable.unpaged()).getContent();
        Instant now = Instant.now();

        for (WorkOrder wo : allMyOrders) {
            String sName = wo.getStatus().name();
            counts.put(sName, counts.getOrDefault(sName, 0) + 1);

            if (wo.getExpectedEndDate() != null && now.isAfter(wo.getExpectedEndDate())
                    && (wo.getStatus() == WorkOrderStatus.OPEN || wo.getStatus() == WorkOrderStatus.ASSIGNED || wo.getStatus() == WorkOrderStatus.IN_PROGRESS)) {
                overdueCount++;
            }
        }

        return TechnicianTaskDto.builder()
                .totalTasks(allMyOrders.size())
                .overdueTasks(overdueCount)
                .countsByStatus(counts)
                .tasks(taskDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TechnicianWorkloadDto> getTechnicianWorkload(String userEmail) {
        User user = getUserByEmail(userEmail);
        if (user.getRole() != User.Role.LAB_MANAGER && user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR) {
            throw new ForbiddenException("Only Lab Managers and Institution Administrators can view technician workloads");
        }

        Long rawInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        final Long instId = rawInstId != null ? rawInstId : 1L;

        List<User> technicians = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.LAB_TECHNICIAN)
                .filter(u -> Objects.equals(u.getInstitutionId() != null ? u.getInstitutionId() : u.getCollegeId(), instId))
                .toList();

        // Use grouped query: List<Object[]> where row = [technicianId, status, totalCount]
        List<Object[]> groupedRows = workOrderRepository.findTechnicianWorkloadGrouped(instId);
        Map<Long, Map<String, Integer>> countsByTech = new HashMap<>();

        for (Object[] row : groupedRows) {
            Long techId = (Long) row[0];
            WorkOrderStatus woStatus = (WorkOrderStatus) row[1];
            Long cnt = (Long) row[2];

            countsByTech.computeIfAbsent(techId, k -> new HashMap<>())
                    .put(woStatus.name(), cnt.intValue());
        }

        Instant now = Instant.now();
        List<TechnicianWorkloadDto> result = new ArrayList<>();

        for (User tech : technicians) {
            Map<String, Integer> techCounts = countsByTech.getOrDefault(tech.getId(), Collections.emptyMap());
            int assignedCount = techCounts.getOrDefault(WorkOrderStatus.ASSIGNED.name(), 0);
            int inProgressCount = techCounts.getOrDefault(WorkOrderStatus.IN_PROGRESS.name(), 0);
            int activeTotal = assignedCount + inProgressCount;

            // Find upcoming scheduled date & overdue count
            List<WorkOrder> upcoming = workOrderRepository.findUpcomingForTechnician(tech.getId(), now);
            Instant nextDate = upcoming.isEmpty() ? null : upcoming.get(0).getExpectedEndDate();

            // Calculate overdue
            List<WorkOrder> techAll = workOrderRepository.findByTechnicianWithFilters(tech.getId(), null, Pageable.unpaged()).getContent();
            int overdue = 0;
            for (WorkOrder wo : techAll) {
                if (wo.getExpectedEndDate() != null && now.isAfter(wo.getExpectedEndDate())
                        && (wo.getStatus() == WorkOrderStatus.ASSIGNED || wo.getStatus() == WorkOrderStatus.IN_PROGRESS)) {
                    overdue++;
                }
            }

            result.add(TechnicianWorkloadDto.builder()
                    .technicianId(tech.getId())
                    .fullName(tech.getFullName())
                    .email(tech.getEmail())
                    .phone(tech.getPhone())
                    .activeTaskCount(activeTotal)
                    .inProgressTaskCount(inProgressCount)
                    .overdueTaskCount(overdue)
                    .nextScheduledDate(nextDate)
                    .build());
        }

        return result;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
