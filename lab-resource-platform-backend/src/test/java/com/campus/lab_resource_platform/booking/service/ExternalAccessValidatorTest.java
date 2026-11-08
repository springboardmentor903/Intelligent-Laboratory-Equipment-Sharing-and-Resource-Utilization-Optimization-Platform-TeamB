package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.AccessGrantRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingListingRepository;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExternalAccessValidatorTest {

    @Mock
    private SharingListingRepository listingRepository;

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private AccessGrantRepository grantRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private OperatingHoursCapacityService capacityService;

    @Mock
    private FeeCalculationService feeCalculationService;

    @InjectMocks
    private ExternalAccessValidator validator;

    private Equipment equipment;
    private User internalUser;
    private User noInstUser;
    private User externalUser;
    private SharingListing listing;
    private SharingAgreement agreement;

    @BeforeEach
    void setUp() {
        equipment = new Equipment();
        equipment.setId(10L);
        equipment.setInstitutionId(100L);
        equipment.setHourlyCost(new BigDecimal("50.00"));
        equipment.setOperatingStartTime(LocalTime.of(8, 0));
        equipment.setOperatingEndTime(LocalTime.of(18, 0));

        internalUser = new User();
        internalUser.setId(1L);
        internalUser.setInstitutionId(100L);

        noInstUser = new User();
        noInstUser.setId(2L);
        noInstUser.setInstitutionId(null);

        externalUser = new User();
        externalUser.setId(3L);
        externalUser.setInstitutionId(200L);

        listing = new SharingListing();
        listing.setId(50L);
        listing.setEquipment(equipment);
        listing.setFeePerHour(new BigDecimal("80.00"));
        listing.setExternalSharePercentage(new BigDecimal("20.00"));
        listing.setMinNoticeHours(12);
        listing.setMaxBookingDurationHours(6);
        listing.setActive(true);

        agreement = new SharingAgreement();
        agreement.setId(99L);
        agreement.setEquipment(equipment);
        agreement.setOwnerInstitutionId(100L);
        agreement.setPartnerInstitutionId(200L);
        agreement.setStartDate(LocalDate.now().minusDays(10));
        agreement.setEndDate(LocalDate.now().plusDays(20));
        agreement.setFeePerHour(new BigDecimal("80.00"));
        agreement.setStatus(AgreementStatus.ACTIVE);

        lenient().when(feeCalculationService.calculateEstimatedFee(any(), any(), any()))
                .thenReturn(new BigDecimal("160.00"));
    }

    @Test
    void testInternalUser_PassesNormallyWithoutAgreement() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);

        ExternalAccessValidator.ValidationResult result = validator.validate(equipment, internalUser, start, end);

        assertFalse(result.isExternal());
        assertNull(result.getSharingAgreementId());
        assertEquals(new BigDecimal("50.00"), result.getFeePerHour());
    }

    @Test
    void testUserWithoutInstitution_PassesNormallyWithoutAgreement() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);

        ExternalAccessValidator.ValidationResult result = validator.validate(equipment, noInstUser, start, end);

        assertFalse(result.isExternal());
        assertNull(result.getSharingAgreementId());
    }

    @Test
    void testExternalUser_FailsWithoutActiveAgreement() {
        when(agreementRepository.findActiveAgreementsForUser(eq(10L), eq(3L), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = start.plusHours(2);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                validator.validate(equipment, externalUser, start, end)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testExternalUser_FailsOnAllowedHoursViolation() {
        agreement.setAllowedStartTime(LocalTime.of(9, 0));
        agreement.setAllowedEndTime(LocalTime.of(17, 0));

        when(agreementRepository.findActiveAgreementsForUser(eq(10L), eq(3L), any(), any(), any()))
                .thenReturn(List.of(agreement));

        // Booking at 08:00 (before allowed start of 09:00)
        LocalDateTime start = LocalDate.now().plusDays(2).atTime(8, 0);
        LocalDateTime end = start.plusHours(2);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                validator.validate(equipment, externalUser, start, end)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("outside allowed agreement hours"));
    }

    @Test
    void testExternalUser_FailsOnNoticeHoursViolation() {
        when(agreementRepository.findActiveAgreementsForUser(eq(10L), eq(3L), any(), any(), any()))
                .thenReturn(List.of(agreement));
        when(listingRepository.findByEquipmentId(10L)).thenReturn(Optional.of(listing));

        // Listing requires 12 hours notice, booking in 2 hours
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                validator.validate(equipment, externalUser, start, end)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("minimum notice"));
    }

    @Test
    void testExternalUser_FailsOnMaxDurationViolation() {
        when(agreementRepository.findActiveAgreementsForUser(eq(10L), eq(3L), any(), any(), any()))
                .thenReturn(List.of(agreement));
        when(listingRepository.findByEquipmentId(10L)).thenReturn(Optional.of(listing));

        // Listing max duration is 6 hours; request 8 hours
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusHours(8);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                validator.validate(equipment, externalUser, start, end)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("maximum allowed duration"));
    }

    @Test
    void testExternalUser_ExceedingWeeklyShareLimit_FailsWithConflict409() {
        when(agreementRepository.findActiveAgreementsForUser(eq(10L), eq(3L), any(), any(), any()))
                .thenReturn(List.of(agreement));
        when(listingRepository.findByEquipmentId(10L)).thenReturn(Optional.of(listing));

        // 7 days * 600 mins = 4200 mins. 20% limit = 840 mins.
        when(capacityService.getWeeklyOperatingHoursCapacity(eq(equipment), any()))
                .thenReturn(4200L);

        // Already booked 700 minutes of external time
        Booking existing = new Booking();
        existing.setStartTime(LocalDateTime.now().plusDays(1).withHour(8).withMinute(0));
        existing.setEndTime(existing.getStartTime().plusMinutes(700));

        when(bookingRepository.findExternalBookingsInPeriod(eq(10L), any(), any(), any()))
                .thenReturn(List.of(existing));
        when(capacityService.getOperatingStartTime(equipment)).thenReturn(LocalTime.of(8, 0));
        when(capacityService.getOperatingEndTime(equipment)).thenReturn(LocalTime.of(18, 0));

        when(capacityService.calculateBookedMinutesInOperatingHours(eq(existing.getStartTime()), eq(existing.getEndTime()), any(), any(), any(), any()))
                .thenReturn(700L);

        // New booking requests 200 minutes (700 + 200 = 900 > 840 limit)
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0);
        LocalDateTime end = start.plusMinutes(200);

        when(capacityService.calculateBookedMinutesInOperatingHours(eq(start), eq(end), any(), any(), any(), any()))
                .thenReturn(200L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                validator.validate(equipment, externalUser, start, end)
        );
        // Requirement explicitly mandates 409 Conflict when weekly external share percentage exceeded
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Weekly external share limit exceeded"));
    }
}

