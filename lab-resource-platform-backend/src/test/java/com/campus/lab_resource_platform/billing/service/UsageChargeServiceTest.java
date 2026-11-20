package com.campus.lab_resource_platform.billing.service;

import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import com.campus.lab_resource_platform.billing.repository.UsageChargeRepository;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UsageChargeServiceTest {

    @Mock
    private UsageChargeRepository chargeRepository;

    @Mock
    private FeeCalculationService feeCalculationService;

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UsageChargeService usageChargeService;

    private Equipment equipment;
    private Department department;
    private User internalUser;
    private User externalUser;
    private User unassignedUser;

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
        department = new Department();
        department.setId(5L);
        department.setName("Bioengineering");

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Flow Cytometer");
        equipment.setInstitutionId(10L);
        equipment.setDepartmentId(5L);
        equipment.setHourlyCost(BigDecimal.valueOf(60.0));

        internalUser = createUser(1L, "internal@inst10.edu", User.Role.RESEARCHER_STUDENT, 10L);
        internalUser.setDepartmentId(5L);

        externalUser = createUser(2L, "external@inst20.edu", User.Role.RESEARCHER_STUDENT, 20L);

        unassignedUser = createUser(3L, "freelancer@gmail.com", User.Role.RESEARCHER_STUDENT, null);
    }

    @Test
    void recordUsageCharge_SkipsCancelledOrZeroUsage() {
        Booking cancelledBooking = Booking.builder()
                .id(10L)
                .status(BookingStatus.CANCELLED)
                .build();

        Optional<UsageCharge> res1 = usageChargeService.recordUsageCharge(cancelledBooking, 60L);
        assertTrue(res1.isEmpty());

        Booking completedBooking = Booking.builder()
                .id(11L)
                .status(BookingStatus.COMPLETED)
                .build();

        Optional<UsageCharge> res2 = usageChargeService.recordUsageCharge(completedBooking, 0L);
        assertTrue(res2.isEmpty());
        verify(chargeRepository, never()).save(any());
    }

    @Test
    void recordUsageCharge_InternalUser() {
        Booking booking = Booking.builder()
                .id(12L)
                .equipment(equipment)
                .user(internalUser)
                .status(BookingStatus.COMPLETED)
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now())
                .build();

        when(chargeRepository.save(any(UsageCharge.class))).thenAnswer(i -> i.getArgument(0));
        when(feeCalculationService.calculateFee(60L, BigDecimal.valueOf(60.0))).thenReturn(BigDecimal.valueOf(60.00));

        Optional<UsageCharge> res = usageChargeService.recordUsageCharge(booking, 60L);

        assertTrue(res.isPresent());
        UsageCharge charge = res.get();
        assertEquals(ChargeType.INTERNAL, charge.getChargeType());
        assertEquals(BigDecimal.valueOf(60.0), charge.getHourlyRateSnapshot());
        assertEquals(60, charge.getUsedMinutes().intValue());
        assertEquals(BigDecimal.valueOf(60.00), charge.getTotalCharge());
    }

    @Test
    void recordUsageCharge_ExternalUserWithAgreement() {
        Booking booking = Booking.builder()
                .id(13L)
                .equipment(equipment)
                .user(externalUser)
                .sharingAgreementId(99L)
                .status(BookingStatus.COMPLETED)
                .build();

        SharingAgreement agreement = new SharingAgreement();
        agreement.setId(99L);
        agreement.setOwnerInstitutionId(10L);
        agreement.setPartnerInstitutionId(20L);
        agreement.setStatus(AgreementStatus.ACTIVE);
        agreement.setFeePerHour(BigDecimal.valueOf(120.0));

        when(agreementRepository.findById(99L)).thenReturn(Optional.of(agreement));
        when(chargeRepository.save(any(UsageCharge.class))).thenAnswer(i -> i.getArgument(0));
        when(feeCalculationService.calculateFee(30L, BigDecimal.valueOf(120.0))).thenReturn(BigDecimal.valueOf(60.00));

        Optional<UsageCharge> res = usageChargeService.recordUsageCharge(booking, 30L);

        assertTrue(res.isPresent());
        UsageCharge charge = res.get();
        assertEquals(ChargeType.EXTERNAL, charge.getChargeType());
        assertEquals(BigDecimal.valueOf(120.0), charge.getHourlyRateSnapshot());
        assertEquals(30, charge.getUsedMinutes().intValue());
        assertEquals(BigDecimal.valueOf(60.00), charge.getTotalCharge());
    }

    @Test
    void recordUsageCharge_UnassignedUser() {
        Booking booking = Booking.builder()
                .id(14L)
                .equipment(equipment)
                .user(unassignedUser)
                .status(BookingStatus.COMPLETED)
                .build();

        when(chargeRepository.save(any(UsageCharge.class))).thenAnswer(i -> i.getArgument(0));
        when(feeCalculationService.calculateFee(120L, BigDecimal.valueOf(60.0))).thenReturn(BigDecimal.valueOf(120.00));

        Optional<UsageCharge> res = usageChargeService.recordUsageCharge(booking, 120L);

        assertTrue(res.isPresent());
        UsageCharge charge = res.get();
        assertEquals(ChargeType.UNASSIGNED, charge.getChargeType());
        assertEquals(BigDecimal.valueOf(60.0), charge.getHourlyRateSnapshot());
        assertEquals(BigDecimal.valueOf(120.00), charge.getTotalCharge());
    }
}
