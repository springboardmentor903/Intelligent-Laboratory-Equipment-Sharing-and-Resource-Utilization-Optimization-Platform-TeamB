package com.campus.lab_resource_platform.calibration.service;

import com.campus.lab_resource_platform.calibration.dto.CalibrationOverviewDto;
import com.campus.lab_resource_platform.calibration.dto.CalibrationReminderDto;
import com.campus.lab_resource_platform.calibration.dto.CalibrationState;
import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.repository.WorkOrderRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CalibrationTrackingServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private CalibrationRecordRepository calibrationRecordRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CalibrationTrackingService calibrationService;

    private User manager;
    private Equipment eq1;
    private Equipment eq2;
    private Equipment eq3;
    private Equipment eq4;

    private Equipment createEquipment(Long id, String name) {
        Equipment e = new Equipment();
        e.setId(id);
        e.setName(name);
        e.setInstitutionId(10L);
        return e;
    }

    private CalibrationRecord createRecord(Equipment eq, LocalDate calDate, LocalDate nextDue) {
        CalibrationRecord rec = new CalibrationRecord();
        rec.setEquipment(eq);
        rec.setCalibrationDate(calDate);
        rec.setNextCalibrationDate(nextDue);
        return rec;
    }

    @BeforeEach
    void setUp() {
        manager = new User();
        manager.setId(1L);
        manager.setEmail("manager@campus.edu");
        manager.setRole(User.Role.LAB_MANAGER);
        manager.setInstitutionId(10L);

        eq1 = createEquipment(101L, "Oscilloscope");
        eq2 = createEquipment(102L, "Multimeter");
        eq3 = createEquipment(103L, "Spectrometer");
        eq4 = createEquipment(104L, "Microbalance");
    }

    @Test
    void getCalibrationOverview_CalculatesStatesCorrectly() {
        LocalDate today = LocalDate.now();

        CalibrationRecord rec1 = createRecord(eq1, today.minusMonths(2), today.plusDays(45));
        CalibrationRecord rec2 = createRecord(eq2, today.minusMonths(5), today.plusDays(10));
        CalibrationRecord rec3 = createRecord(eq3, today.minusMonths(12), today.minusDays(5));

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(equipmentRepository.findByInstitutionIdOrCollegeId(10L, 10L)).thenReturn(List.of(eq1, eq2, eq3, eq4));
        when(calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(101L)).thenReturn(List.of(rec1));
        when(calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(102L)).thenReturn(List.of(rec2));
        when(calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(103L)).thenReturn(List.of(rec3));
        when(calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(104L)).thenReturn(Collections.emptyList());

        Page<CalibrationOverviewDto> result = calibrationService.getCalibrationOverview(
                null, null, null, manager.getEmail(), PageRequest.of(0, 10));

        assertEquals(4, result.getTotalElements());

        CalibrationOverviewDto dto1 = result.getContent().stream().filter(d -> d.getEquipmentId().equals(101L)).findFirst().orElseThrow();
        assertEquals(CalibrationState.VALID, dto1.getCalibrationState());

        CalibrationOverviewDto dto2 = result.getContent().stream().filter(d -> d.getEquipmentId().equals(102L)).findFirst().orElseThrow();
        assertEquals(CalibrationState.DUE_SOON, dto2.getCalibrationState());

        CalibrationOverviewDto dto3 = result.getContent().stream().filter(d -> d.getEquipmentId().equals(103L)).findFirst().orElseThrow();
        assertEquals(CalibrationState.EXPIRED, dto3.getCalibrationState());

        CalibrationOverviewDto dto4 = result.getContent().stream().filter(d -> d.getEquipmentId().equals(104L)).findFirst().orElseThrow();
        assertEquals(CalibrationState.NONE, dto4.getCalibrationState());
    }

    @Test
    void getRenewalReminders_ReturnsOnlyDueSoonAndExpired() {
        LocalDate today = LocalDate.now();

        CalibrationRecord dueSoonRec = createRecord(eq2, today.minusMonths(5), today.plusDays(5));
        CalibrationRecord expiredRec = createRecord(eq3, today.minusMonths(12), today.minusDays(2));

        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(equipmentRepository.findByInstitutionIdOrCollegeId(10L, 10L)).thenReturn(List.of(eq2, eq3));
        when(calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(102L)).thenReturn(List.of(dueSoonRec));
        when(calibrationRecordRepository.findByEquipmentIdOrderByCalibrationDateDesc(103L)).thenReturn(List.of(expiredRec));
        when(workOrderRepository.existsActiveWorkOrderByEquipmentAndType(any(), any(), any())).thenReturn(false);

        List<CalibrationReminderDto> reminders = calibrationService.getReminders(manager.getEmail());

        assertEquals(2, reminders.size());
        assertTrue(reminders.stream().anyMatch(r -> r.getCalibrationState() == CalibrationState.DUE_SOON));
        assertTrue(reminders.stream().anyMatch(r -> r.getCalibrationState() == CalibrationState.EXPIRED));
    }
}
