package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.PreventiveScheduleDto;
import com.campus.lab_resource_platform.maintenance.dto.PreventiveScheduleResponse;
import com.campus.lab_resource_platform.maintenance.entity.PreventiveSchedule;
import com.campus.lab_resource_platform.maintenance.repository.PreventiveScheduleRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class PreventiveScheduleService {

    private final PreventiveScheduleRepository scheduleRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public PreventiveScheduleService(
            PreventiveScheduleRepository scheduleRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository) {
        this.scheduleRepository = scheduleRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PreventiveScheduleResponse createSchedule(PreventiveScheduleDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + dto.getEquipmentId()));

        validateLabManagerOfEquipment(user, equipment);

        LocalDate nextDue = dto.getNextDueDate() != null
                ? dto.getNextDueDate()
                : LocalDate.now().plusDays(dto.getCustomRepeatDays() != null && dto.getCustomRepeatDays() > 0
                ? dto.getCustomRepeatDays()
                : dto.getRepeatPeriod().getDays());

        PreventiveSchedule schedule = PreventiveSchedule.builder()
                .equipment(equipment)
                .title(dto.getTitle().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : "")
                .repeatPeriod(dto.getRepeatPeriod())
                .customRepeatDays(dto.getCustomRepeatDays())
                .lastCompletedDate(dto.getLastCompletedDate())
                .nextDueDate(nextDue)
                .active(true)
                .build();

        PreventiveSchedule saved = scheduleRepository.save(schedule);
        return PreventiveScheduleResponse.fromEntity(saved);
    }

    @Transactional
    public PreventiveScheduleResponse updateSchedule(Long id, PreventiveScheduleDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        PreventiveSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Preventive schedule not found with ID: " + id));

        validateLabManagerOfEquipment(user, schedule.getEquipment());

        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            schedule.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            schedule.setDescription(dto.getDescription().trim());
        }
        if (dto.getRepeatPeriod() != null) {
            schedule.setRepeatPeriod(dto.getRepeatPeriod());
        }
        if (dto.getCustomRepeatDays() != null) {
            schedule.setCustomRepeatDays(dto.getCustomRepeatDays());
        }
        if (dto.getLastCompletedDate() != null) {
            schedule.setLastCompletedDate(dto.getLastCompletedDate());
        }
        if (dto.getNextDueDate() != null) {
            schedule.setNextDueDate(dto.getNextDueDate());
        }

        PreventiveSchedule saved = scheduleRepository.save(schedule);
        return PreventiveScheduleResponse.fromEntity(saved);
    }

    @Transactional
    public PreventiveScheduleResponse activateSchedule(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        PreventiveSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Preventive schedule not found with ID: " + id));

        validateLabManagerOfEquipment(user, schedule.getEquipment());
        schedule.setActive(true);
        PreventiveSchedule saved = scheduleRepository.save(schedule);
        return PreventiveScheduleResponse.fromEntity(saved);
    }

    @Transactional
    public PreventiveScheduleResponse deactivateSchedule(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        PreventiveSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Preventive schedule not found with ID: " + id));

        validateLabManagerOfEquipment(user, schedule.getEquipment());
        schedule.setActive(false);
        PreventiveSchedule saved = scheduleRepository.save(schedule);
        return PreventiveScheduleResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<PreventiveScheduleResponse> getSchedules(Long equipmentId, Boolean activeOnly, String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        return scheduleRepository.findByInstitutionWithFilters(instId, equipmentId, activeOnly, pageable)
                .map(PreventiveScheduleResponse::fromEntity);
    }

    private void validateNotSystemAdmin(User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ForbiddenException("System Administrators do not have access to maintenance operations");
        }
    }

    private void validateLabManagerOfEquipment(User user, Equipment equipment) {
        if (user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only the Lab Manager can configure preventive maintenance schedules");
        }
        Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
        if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
            throw new ForbiddenException("Only the Lab Manager of the equipment's institution can configure its schedules");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
