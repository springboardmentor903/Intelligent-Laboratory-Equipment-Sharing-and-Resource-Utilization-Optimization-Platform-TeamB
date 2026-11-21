package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.PreventiveScheduleDto;
import com.campus.lab_resource_platform.maintenance.dto.PreventiveScheduleResponse;
import com.campus.lab_resource_platform.maintenance.entity.*;
import com.campus.lab_resource_platform.maintenance.repository.PreventiveScheduleRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PreventiveScheduleServiceTest {

    @Mock
    private PreventiveScheduleRepository scheduleRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PreventiveScheduleService scheduleService;

    private User labManager;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        labManager = new User();
        labManager.setId(1L);
        labManager.setEmail("manager@lab.edu");
        labManager.setRole(User.Role.LAB_MANAGER);
        labManager.setInstitutionId(10L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Centrifuge");
        equipment.setInstitutionId(10L);
    }

    @Test
    void calculateScheduleStatus_CorrectlyIdentifiesStates() {
        LocalDate today = LocalDate.now();

        // Overdue
        PreventiveSchedule overdue = PreventiveSchedule.builder()
                .equipment(equipment)
                .active(true)
                .nextDueDate(today.minusDays(2))
                .build();
        assertEquals(ScheduleStatus.OVERDUE, overdue.calculateStatus(today));

        // Due soon (within 7 days)
        PreventiveSchedule dueSoon = PreventiveSchedule.builder()
                .equipment(equipment)
                .active(true)
                .nextDueDate(today.plusDays(4))
                .build();
        assertEquals(ScheduleStatus.DUE_SOON, dueSoon.calculateStatus(today));

        // OK (more than 7 days)
        PreventiveSchedule ok = PreventiveSchedule.builder()
                .equipment(equipment)
                .active(true)
                .nextDueDate(today.plusDays(20))
                .build();
        assertEquals(ScheduleStatus.OK, ok.calculateStatus(today));
    }

    @Test
    void createSchedule_Success() {
        when(userRepository.findByEmail(labManager.getEmail())).thenReturn(Optional.of(labManager));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(scheduleRepository.save(any(PreventiveSchedule.class))).thenAnswer(i -> {
            PreventiveSchedule s = i.getArgument(0);
            s.setId(10L);
            return s;
        });

        PreventiveScheduleDto dto = new PreventiveScheduleDto();
        dto.setEquipmentId(100L);
        dto.setTitle("Monthly Rotor Inspection");
        dto.setRepeatPeriod(ScheduleRepeatPeriod.MONTHLY);
        dto.setNextDueDate(LocalDate.now().plusMonths(1));

        PreventiveScheduleResponse res = scheduleService.createSchedule(dto, labManager.getEmail());

        assertNotNull(res);
        assertEquals(10L, res.getId());
        assertEquals("Monthly Rotor Inspection", res.getTitle());
        assertEquals(ScheduleRepeatPeriod.MONTHLY, res.getRepeatPeriod());
        assertEquals(ScheduleStatus.OK, res.getStatus());
    }
}
