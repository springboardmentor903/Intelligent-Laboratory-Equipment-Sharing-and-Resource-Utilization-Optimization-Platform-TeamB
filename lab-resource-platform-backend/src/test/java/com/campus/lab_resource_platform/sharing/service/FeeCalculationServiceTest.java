
package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.sharing.dto.AgreementUsageFeesResponse;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.repository.AccessGrantRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FeeCalculationServiceTest {

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private AccessGrantRepository grantRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UtilizationRepository utilizationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FeeCalculationService feeService;

    private User ownerAdmin;
    private SharingAgreement agreement;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        ownerAdmin = new User();
        ownerAdmin.setId(10L);
        ownerAdmin.setEmail("admin@inst1.edu");
        ownerAdmin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        ownerAdmin.setInstitutionId(1L);
        ownerAdmin.setActive(true);

        equipment = new Equipment();
        equipment.setId(50L);
        equipment.setName("NMR Spectrometer");

        agreement = new SharingAgreement();
        agreement.setId(100L);
        agreement.setEquipment(equipment);
        agreement.setOwnerInstitutionId(1L);
        agreement.setPartnerInstitutionId(2L);
        agreement.setFeePerHour(new BigDecimal("120.00"));

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
    void testCalculateFee_WholeMinutesAndHalfUpRounding() {
        BigDecimal fee1 = feeService.calculateFee(90, new BigDecimal("100.00"));
        assertEquals(new BigDecimal("150.00"), fee1);

        BigDecimal fee2 = feeService.calculateFee(45, new BigDecimal("75.50"));
        assertEquals(new BigDecimal("56.63"), fee2);

        BigDecimal fee3 = feeService.calculateFee(17, new BigDecimal("33.33"));
        assertEquals(new BigDecimal("9.44"), fee3);
    }

    @Test
    void testCalculateEstimatedFee() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 10, 11, 30);
        BigDecimal feePerHour = new BigDecimal("80.00");

        BigDecimal est = feeService.calculateEstimatedFee(start, end, feePerHour);
        assertEquals(new BigDecimal("200.00"), est);
    }

    @Test
    void testCalculateActualFee() {
        BigDecimal actual = feeService.calculateActualFee(75, new BigDecimal("60.00"));
        assertEquals(new BigDecimal("75.00"), actual);
    }

    @Test
    void testGetAgreementUsageFees_Success() {
        when(userRepository.findByEmail(ownerAdmin.getEmail()))
                .thenReturn(Optional.of(ownerAdmin));

        when(agreementRepository.findById(100L))
                .thenReturn(Optional.of(agreement));

        Booking b1 = new Booking();
        b1.setId(1L);
        b1.setStartTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        b1.setEndTime(LocalDateTime.of(2026, 11, 10, 12, 0));
        b1.setStatus(BookingStatus.COMPLETED);

        Utilization u1 = new Utilization();
        u1.setBooking(b1);
        u1.setUsedMinutes(110);
        u1.setActualFee(new BigDecimal("220.00"));

        when(bookingRepository.findAgreementBookingsInPeriod(eq(100L), any(), any()))
                .thenReturn(List.of(b1));

        when(utilizationRepository.findByBookingIdIn(List.of(1L)))
                .thenReturn(List.of(u1));

        AgreementUsageFeesResponse resp = feeService.getAgreementUsageFees(
                100L,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );

        assertNotNull(resp);
        assertEquals(100L, resp.getAgreementId());
        assertEquals(120L, resp.getTotalBookedMinutes());
        assertEquals(110L, resp.getTotalUsedMinutes());
        assertEquals(new BigDecimal("240.00"), resp.getTotalEstimatedFee());
        assertEquals(new BigDecimal("220.00"), resp.getTotalActualFee());
        assertEquals(1, resp.getItems().size());
    }

    @Test
    void testGetAgreementUsageFees_ForbiddenForUnrelatedUser() {
        User unrelatedUser = new User();
        unrelatedUser.setId(999L);
        unrelatedUser.setEmail("unrelated@other.edu");
        unrelatedUser.setRole(User.Role.RESEARCHER_STUDENT);
        unrelatedUser.setInstitutionId(999L);
        unrelatedUser.setActive(true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        unrelatedUser.getEmail(),
                        null,
                        Collections.emptyList()
                )
        );

        when(userRepository.findByEmail(unrelatedUser.getEmail()))
                .thenReturn(Optional.of(unrelatedUser));

        when(agreementRepository.findById(100L))
                .thenReturn(Optional.of(agreement));

        when(grantRepository.findByAgreementIdAndUserId(100L, 999L))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> feeService.getAgreementUsageFees(100L, null, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
