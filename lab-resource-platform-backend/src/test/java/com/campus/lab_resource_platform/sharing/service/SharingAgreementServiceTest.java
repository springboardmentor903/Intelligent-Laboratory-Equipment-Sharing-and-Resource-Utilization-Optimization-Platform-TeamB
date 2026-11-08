package com.campus.lab_resource_platform.sharing.service;

import java.util.Collections;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.AccessGrantResponse;
import com.campus.lab_resource_platform.sharing.dto.RevokeRequestDto;
import com.campus.lab_resource_platform.sharing.dto.SharingAgreementResponse;
import com.campus.lab_resource_platform.sharing.entity.AccessGrant;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.event.ReleasedTimeEvent;
import com.campus.lab_resource_platform.sharing.repository.AccessGrantRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SharingAgreementServiceTest {

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private AccessGrantRepository grantRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionService institutionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SharingAgreementService agreementService;

    private User ownerAdmin;
    private User granteeUser;
    private Equipment equipment;
    private SharingAgreement agreement;
    private AccessGrant grant;

    @BeforeEach
    void setUp() {
        ownerAdmin = new User();
        ownerAdmin.setId(10L);
        ownerAdmin.setEmail("owneradmin@inst1.edu");
        ownerAdmin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        ownerAdmin.setInstitutionId(1L);
        ownerAdmin.setActive(true);

        granteeUser = new User();
        granteeUser.setId(20L);
        granteeUser.setEmail("researcher@inst2.edu");
        granteeUser.setFullName("External Researcher");
        granteeUser.setRole(User.Role.RESEARCHER_STUDENT);
        granteeUser.setInstitutionId(2L);
        granteeUser.setActive(true);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Laser Spectrometer");
        equipment.setAssetTag("LS-01");
        equipment.setInstitutionId(1L);

        agreement = new SharingAgreement();
        agreement.setId(500L);
        agreement.setEquipment(equipment);
        agreement.setOwnerInstitutionId(1L);
        agreement.setPartnerInstitutionId(2L);
        agreement.setStartDate(LocalDate.now().minusDays(5));
        agreement.setEndDate(LocalDate.now().plusDays(25));
        agreement.setFeePerHour(new BigDecimal("95.00"));
        agreement.setStatus(AgreementStatus.ACTIVE);
        agreement.setApprovedByUserId(10L);

        grant = new AccessGrant();
        grant.setId(600L);
        grant.setAgreement(agreement);
        grant.setUser(granteeUser);
        grant.setStatus(GrantStatus.ACTIVE);

        agreement.setGrants(new ArrayList<>(List.of(grant)));

       SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
                ownerAdmin.getEmail(),
                null,
                Collections.emptyList()
        )
);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testIsExpired_CalculatesDynamically() {
        agreement.setEndDate(LocalDate.now().minusDays(1));
        assertTrue(agreement.isExpired());

        agreement.setEndDate(LocalDate.now().plusDays(1));
        assertFalse(agreement.isExpired());
    }

    @Test
    void testRevokeAgreement_SuccessAndCancelsFutureBookings() {
        when(userRepository.findByEmail(ownerAdmin.getEmail())).thenReturn(Optional.of(ownerAdmin));
        when(agreementRepository.findWithGrantsById(500L)).thenReturn(Optional.of(agreement));
        when(agreementRepository.save(any(SharingAgreement.class))).thenAnswer(i -> i.getArgument(0));

        // Mock future pending/confirmed bookings to be cancelled
        Booking futureBooking = new Booking();
        futureBooking.setId(701L);
        futureBooking.setEquipment(equipment);
        futureBooking.setUser(granteeUser);
        futureBooking.setStartTime(LocalDateTime.now().plusDays(2));
        futureBooking.setEndTime(LocalDateTime.now().plusDays(2).plusHours(3));
        futureBooking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findBySharingAgreementIdAndStartTimeAfterAndStatusIn(eq(500L), any(), any()))
                .thenReturn(List.of(futureBooking));

        SharingAgreementResponse resp = agreementService.revokeAgreement(
                500L, new RevokeRequestDto("Safety non-compliance breach")
        );

        assertNotNull(resp);
        assertEquals(AgreementStatus.REVOKED, resp.getStatus());
        assertEquals("Safety non-compliance breach", resp.getRevocationReason());

        // Verify grant is also revoked
        assertEquals(GrantStatus.REVOKED, grant.getStatus());

        // Verify future booking is cancelled
        assertEquals(BookingStatus.CANCELLED, futureBooking.getStatus());
        verify(bookingRepository).save(futureBooking);

        // Verify ReleasedTimeEvent was published
        verify(eventPublisher).publishEvent(any(ReleasedTimeEvent.class));
    }

    @Test
    void testRevokeAgreement_RequiresReason() {
        

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                agreementService.revokeAgreement(500L, new RevokeRequestDto(""))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("mandatory"));
    }

    @Test
    void testRevokeGrant_CancelsFutureBookingsForUserOnly() {
        when(userRepository.findByEmail(ownerAdmin.getEmail())).thenReturn(Optional.of(ownerAdmin));
        when(grantRepository.findById(600L)).thenReturn(Optional.of(grant));
        when(grantRepository.save(any(AccessGrant.class))).thenAnswer(i -> i.getArgument(0));

        Booking futureBooking = new Booking();
        futureBooking.setId(702L);
        futureBooking.setEquipment(equipment);
        futureBooking.setUser(granteeUser);
        futureBooking.setStartTime(LocalDateTime.now().plusDays(1));
        futureBooking.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        futureBooking.setStatus(BookingStatus.PENDING_APPROVAL);

        when(bookingRepository.findBySharingAgreementIdAndUserIdAndStartTimeAfterAndStatusIn(
                eq(500L), eq(20L), any(), any()))
                .thenReturn(List.of(futureBooking));

        AccessGrantResponse resp = agreementService.revokeGrant(600L, new RevokeRequestDto("User account suspended"));
        assertEquals(GrantStatus.REVOKED, resp.getStatus());
        assertEquals(BookingStatus.CANCELLED, futureBooking.getStatus());
        verify(eventPublisher).publishEvent(any(ReleasedTimeEvent.class));
    }
}

