package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentCategory;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.*;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.entity.UsageType;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SharingAnalyticsServiceTest {

    @Mock
    private UtilizationRepository utilizationRepository;

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionService institutionService;

    @Spy
    private FeeCalculationService feeCalculationService = new FeeCalculationService(
            null, null, null, null, null);

    @InjectMocks
    private SharingAnalyticsService analyticsService;

    private User instAdmin1;
    private User instAdmin2;
    private User sysAdmin;
    private User studentUser;

    private Equipment equipInst1;
    private Equipment equipInst2;

    @BeforeEach
    void setUp() {
        instAdmin1 = new User();
        instAdmin1.setId(1L);
        instAdmin1.setEmail("admin1@college1.edu");
        instAdmin1.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        instAdmin1.setInstitutionId(100L);

        instAdmin2 = new User();
        instAdmin2.setId(2L);
        instAdmin2.setEmail("admin2@college2.edu");
        instAdmin2.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        instAdmin2.setInstitutionId(200L);

        sysAdmin = new User();
        sysAdmin.setId(3L);
        sysAdmin.setEmail("sysadmin@platform.org");
        sysAdmin.setRole(User.Role.SYSTEM_ADMINISTRATOR);

        studentUser = new User();
        studentUser.setId(4L);
        studentUser.setEmail("student@college1.edu");
        studentUser.setRole(User.Role.RESEARCHER_STUDENT);
        studentUser.setInstitutionId(100L);

        EquipmentCategory cat1 = new EquipmentCategory();
        cat1.setId(1L);
        cat1.setName("Microscopy");

        EquipmentCategory cat2 = new EquipmentCategory();
        cat2.setId(2L);
        cat2.setName("Spectrometry");

        equipInst1 = Equipment.builder()
                .id(10L)
                .name("Electron Microscope")
                .assetTag("EM-001")
                .institutionId(100L)
                .hourlyCost(BigDecimal.valueOf(100))
                .category(cat1)
                .build();

        equipInst2 = Equipment.builder()
                .id(20L)
                .name("Mass Spectrometer")
                .assetTag("MS-002")
                .institutionId(200L)
                .hourlyCost(BigDecimal.valueOf(150))
                .category(cat2)
                .build();

        lenient().when(institutionService.getInstitutionName(100L)).thenReturn("College 1");
        lenient().when(institutionService.getInstitutionName(200L)).thenReturn("College 2");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SharingAgreement createAgreement(Long id, Long ownerInst, Long partnerInst, Equipment equip,
                                             AgreementStatus status, LocalDate start, LocalDate end, BigDecimal fee) {
        SharingAgreement a = new SharingAgreement();
        a.setId(id);
        a.setOwnerInstitutionId(ownerInst);
        a.setPartnerInstitutionId(partnerInst);
        a.setEquipment(equip);
        a.setStatus(status);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setFeePerHour(fee);
        return a;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList())
        );
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void testSummary_Success_ForInstitutionAdmin() {
        authenticateAs(instAdmin1);

        // 1. Provided utilization (Equipment of Inst 1 used by Inst 2 user for 120 minutes)
        User externalUser = new User();
        externalUser.setId(21L);
        externalUser.setInstitutionId(200L);

        Booking bookingProvided = Booking.builder()
                .id(50L)
                .equipment(equipInst1)
                .user(externalUser)
                .external(true)
                .feePerHour(BigDecimal.valueOf(60))
                .status(BookingStatus.COMPLETED)
                .build();

        Utilization utilProvided = Utilization.builder()
                .id(501L)
                .equipment(equipInst1)
                .user(externalUser)
                .booking(bookingProvided)
                .usageType(UsageType.SHARED)
                .usedMinutes(120) // 2 hours
                .checkInTime(Instant.now().minus(Duration.ofHours(5)))
                .checkOutTime(Instant.now().minus(Duration.ofHours(3)))
                .build();

        when(utilizationRepository.findAllProvidedUtilizations(100L))
                .thenReturn(List.of(utilProvided));

        // 2. Used utilization (Inst 1 student used Equipment of Inst 2 for 60 minutes)
        Booking bookingUsed = Booking.builder()
                .id(60L)
                .equipment(equipInst2)
                .user(studentUser)
                .external(true)
                .feePerHour(BigDecimal.valueOf(80))
                .status(BookingStatus.COMPLETED)
                .build();

        Utilization utilUsed = Utilization.builder()
                .id(601L)
                .equipment(equipInst2)
                .user(studentUser)
                .booking(bookingUsed)
                .usageType(UsageType.SHARED)
                .usedMinutes(60) // 1 hour
                .checkInTime(Instant.now().minus(Duration.ofHours(2)))
                .checkOutTime(Instant.now().minus(Duration.ofHours(1)))
                .build();

        when(utilizationRepository.findAllUsedExternalUtilizations(100L))
                .thenReturn(List.of(utilUsed));

        // 3. Expected fees: Upcoming external booking for 90 mins at 60/hr = 90
        Booking upcomingBooking = Booking.builder()
                .id(70L)
                .equipment(equipInst1)
                .user(externalUser)
                .external(true)
                .feePerHour(BigDecimal.valueOf(60))
                .status(BookingStatus.CONFIRMED)
                .startTime(Instant.now().plus(Duration.ofHours(1)))
                .endTime(Instant.now().plus(Duration.ofHours(2).plus(Duration.ofMinutes(30)))) // 90 min
                .build();

        when(bookingRepository.findExternalBookingsForEquipmentOwnerAndStatuses(eq(100L), anyCollection()))
                .thenReturn(List.of(upcomingBooking));

        // 4. Bookings under agreements: 3 bookings, 1 no-show
        Booking noShowBooking = Booking.builder()
                .id(80L)
                .equipment(equipInst1)
                .user(externalUser)
                .external(true)
                .status(BookingStatus.NO_SHOW)
                .build();

        when(bookingRepository.findAllSharingBookingsForInstitution(100L))
                .thenReturn(List.of(bookingProvided, upcomingBooking, noShowBooking));

        // 5. Agreements: 1 active expiring in 15 days, 1 expired
        SharingAgreement activeAgr = createAgreement(
                101L, 100L, 200L, equipInst1, AgreementStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(15), BigDecimal.valueOf(60));

        SharingAgreement expiredAgr = createAgreement(
                102L, 100L, 300L, equipInst1, AgreementStatus.ACTIVE,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(10), BigDecimal.valueOf(60));

        when(agreementRepository.findAllByInstitutionInvolved(100L))
                .thenReturn(List.of(activeAgr, expiredAgr));

        // 6. Total equipment usage (internal 120 + external 120 = 240 mins)
        Utilization internalUtil = Utilization.builder()
                .id(502L)
                .equipment(equipInst1)
                .user(studentUser)
                .usageType(UsageType.INTERNAL)
                .usedMinutes(120)
                .build();

        when(utilizationRepository.findAllByEquipmentInstitution(100L))
                .thenReturn(List.of(utilProvided, internalUtil));

        // Execute
        SharingAnalyticsSummaryResponse summary = analyticsService.getSummary(null);

        // Assertions
        assertNotNull(summary);
        assertEquals(100L, summary.getInstitutionId());
        assertEquals("College 1", summary.getInstitutionName());

        // Actual usage hours: 120 min = 2.00, 60 min = 1.00
        assertEquals(new BigDecimal("2.00"), summary.getTotalHoursProvided());
        assertEquals(new BigDecimal("1.00"), summary.getTotalHoursUsed());

        // Fees: 120 mins @ 60/hr = 120.00 earned; 60 mins @ 80/hr = 80.00 owed
        assertEquals(new BigDecimal("120.00"), summary.getFeesEarned());
        assertEquals(new BigDecimal("80.00"), summary.getFeesOwed());
        assertEquals(new BigDecimal("40.00"), summary.getNetBalance());

        // Expected fees: 90 mins @ 60/hr = 90.00
        assertEquals(new BigDecimal("90.00"), summary.getExpectedFees());

        // Bookings & No-show: 3 bookings, 1 no-show -> 33.33%
        assertEquals(3L, summary.getTotalBookings());
        assertEquals(1L, summary.getNoShowCount());
        assertEquals(new BigDecimal("33.33"), summary.getNoShowRate());

        // Agreements: 1 active, 1 expiring in <= 30 days, 2 partners (200, 300)
        assertEquals(1L, summary.getActiveAgreements());
        assertEquals(1L, summary.getExpiringAgreementsCount());
        assertEquals(2L, summary.getTotalPartners());

        // External share percentage: 120 / 240 * 100 = 50.00%
        assertEquals(new BigDecimal("50.00"), summary.getExternalSharePercentage());
    }

    @Test
    void testSummary_Forbidden_ForUnauthorizedRoles() {
        authenticateAs(studentUser);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                analyticsService.getSummary(100L)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testSummary_Forbidden_InstitutionAdminAccessingOtherInstitution() {
        authenticateAs(instAdmin1); // belongs to 100L

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                analyticsService.getSummary(200L) // trying to access 200L
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testSummary_Success_SystemAdminAccessingAnyInstitution() {
        authenticateAs(sysAdmin);

        when(utilizationRepository.findAllProvidedUtilizations(200L)).thenReturn(Collections.emptyList());
        when(utilizationRepository.findAllUsedExternalUtilizations(200L)).thenReturn(Collections.emptyList());
        when(bookingRepository.findExternalBookingsForEquipmentOwnerAndStatuses(eq(200L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findAllSharingBookingsForInstitution(200L)).thenReturn(Collections.emptyList());
        when(agreementRepository.findAllByInstitutionInvolved(200L)).thenReturn(Collections.emptyList());
        when(utilizationRepository.findAllByEquipmentInstitution(200L)).thenReturn(Collections.emptyList());

        SharingAnalyticsSummaryResponse summary = analyticsService.getSummary(200L);
        assertNotNull(summary);
        assertEquals(200L, summary.getInstitutionId());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.getTotalHoursProvided());
    }

    @Test
    void testTrends_Monthly_IncludesZeroPeriods() {
        authenticateAs(instAdmin1);

        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);

        when(utilizationRepository.findAllProvidedUtilizations(100L)).thenReturn(Collections.emptyList());
        when(utilizationRepository.findAllUsedExternalUtilizations(100L)).thenReturn(Collections.emptyList());

        SharingTrendResponse response = analyticsService.getTrends(null, "MONTHLY", startDate, endDate);

        assertNotNull(response);
        assertEquals("MONTHLY", response.getPeriod());
        assertEquals(3, response.getItems().size()); // Aug, Sep, Oct 2026

        for (SharingTrendItem item : response.getItems()) {
            assertEquals(BigDecimal.ZERO.setScale(2), item.getHoursProvided());
            assertEquals(BigDecimal.ZERO.setScale(2), item.getHoursUsed());
            assertEquals(BigDecimal.ZERO.setScale(2), item.getFeesEarned());
            assertEquals(BigDecimal.ZERO.setScale(2), item.getFeesOwed());
            assertEquals(BigDecimal.ZERO.setScale(2), item.getNetBalance());
        }

        assertEquals("2026-08", response.getItems().get(0).getPeriodLabel());
        assertEquals("2026-09", response.getItems().get(1).getPeriodLabel());
        assertEquals("2026-10", response.getItems().get(2).getPeriodLabel());
    }

    @Test
    void testPartnerAnalytics_SortingAndPagination() {
        authenticateAs(instAdmin1);

        // Partner 200 setup
        SharingAgreement agr = createAgreement(
                1L, 100L, 200L, equipInst1, AgreementStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), BigDecimal.valueOf(50));
        when(agreementRepository.findAllByInstitutionInvolved(100L)).thenReturn(List.of(agr));

        User partnerUser = new User();
        partnerUser.setId(25L);
        partnerUser.setInstitutionId(200L);

        Utilization provUtil = Utilization.builder()
                .id(1L)
                .equipment(equipInst1)
                .user(partnerUser)
                .usageType(UsageType.SHARED)
                .usedMinutes(180) // 3 hours
                .actualFee(BigDecimal.valueOf(150))
                .build();

        when(utilizationRepository.findAllProvidedUtilizations(100L)).thenReturn(List.of(provUtil));
        when(utilizationRepository.findAllUsedExternalUtilizations(100L)).thenReturn(Collections.emptyList());
        when(bookingRepository.findAllSharingBookingsForInstitution(100L)).thenReturn(Collections.emptyList());

        Page<PartnerAnalyticsItem> page = analyticsService.getPartnerAnalytics(
                null, PageRequest.of(0, 10), "balance", "DESC");

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        PartnerAnalyticsItem item = page.getContent().get(0);
        assertEquals(200L, item.getPartnerInstitutionId());
        assertEquals(new BigDecimal("3.00"), item.getHoursProvided());
        assertEquals(new BigDecimal("150.00"), item.getFeesEarned());
        assertEquals(new BigDecimal("150.00"), item.getBalance());
        assertEquals(1L, item.getActiveAgreementsCount());
    }

    @Test
    void testPartnershipReport_Success() {
        authenticateAs(instAdmin1);

        User partnerUser = new User();
        partnerUser.setId(26L);
        partnerUser.setInstitutionId(200L);

        Utilization provUtil = Utilization.builder()
                .id(1L)
                .equipment(equipInst1)
                .user(partnerUser)
                .usageType(UsageType.SHARED)
                .usedMinutes(60)
                .actualFee(BigDecimal.valueOf(50))
                .checkInTime(Instant.now().minus(Duration.ofDays(5)))
                .build();

        when(utilizationRepository.findAllProvidedUtilizations(100L)).thenReturn(List.of(provUtil));
        when(utilizationRepository.findAllUsedExternalUtilizations(100L)).thenReturn(Collections.emptyList());
        when(bookingRepository.findAllSharingBookingsForInstitution(100L)).thenReturn(Collections.emptyList());

        SharingAgreement agr = createAgreement(
                1L, 100L, 200L, equipInst1, AgreementStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), BigDecimal.valueOf(50));

        when(agreementRepository.findAgreementsBetweenInstitutions(100L, 200L))
                .thenReturn(List.of(agr));

        PartnershipReportResponse report = analyticsService.getPartnershipReport(null, 200L);

        assertNotNull(report);
        assertEquals(100L, report.getInstitutionId());
        assertEquals(200L, report.getPartnerInstitutionId());
        assertEquals(new BigDecimal("1.00"), report.getHoursProvided());
        assertEquals(new BigDecimal("50.00"), report.getFeesEarned());
        assertEquals(1, report.getAgreements().size());
        assertEquals("ACTIVE", report.getAgreements().get(0).getStatus());
        assertFalse(report.getMonthlyTrends().isEmpty());
    }

    @Test
    void testPartnershipReport_SelfReportRejected() {
        authenticateAs(instAdmin1); // belongs to 100L

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                analyticsService.getPartnershipReport(null, 100L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testMostSharedEquipment_Success() {
        authenticateAs(instAdmin1);

        when(equipmentRepository.findByInstitutionIdOrCollegeId(100L, 100L))
                .thenReturn(List.of(equipInst1));

        User partnerUser = new User();
        partnerUser.setId(30L);
        partnerUser.setInstitutionId(200L);

        Utilization extUtil = Utilization.builder()
                .id(1L)
                .equipment(equipInst1)
                .user(partnerUser)
                .usageType(UsageType.SHARED)
                .usedMinutes(180)
                .actualFee(BigDecimal.valueOf(300))
                .build();

        Utilization internalUtil = Utilization.builder()
                .id(2L)
                .equipment(equipInst1)
                .user(studentUser)
                .usageType(UsageType.INTERNAL)
                .usedMinutes(120)
                .build();

        when(utilizationRepository.findAllProvidedUtilizations(100L)).thenReturn(List.of(extUtil));
        when(utilizationRepository.findAllByEquipmentInstitution(100L)).thenReturn(List.of(extUtil, internalUtil));

        Booking extBooking = Booking.builder()
                .id(99L)
                .equipment(equipInst1)
                .external(true)
                .build();
        when(bookingRepository.findExternalBookingsByEquipmentId(10L)).thenReturn(List.of(extBooking));

        Page<MostSharedEquipmentItem> page = analyticsService.getMostSharedEquipment(
                null, PageRequest.of(0, 10), "providedMinutes", "DESC");

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        MostSharedEquipmentItem item = page.getContent().get(0);
        assertEquals(10L, item.getEquipmentId());
        assertEquals("Electron Microscope", item.getEquipmentName());
        assertEquals(180L, item.getProvidedMinutes());
        assertEquals(new BigDecimal("3.00"), item.getProvidedHours());
        assertEquals(300L, item.getTotalUsageMinutes());
        assertEquals(new BigDecimal("5.00"), item.getTotalUsageHours());
        assertEquals(new BigDecimal("300.00"), item.getFeesEarned());
        // 180 / 300 * 100 = 60.00%
        assertEquals(new BigDecimal("60.00"), item.getExternalSharePercentage());
        assertEquals(1L, item.getBookingCount());
    }
}

