package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.service.OperatingHoursCapacityService;
import com.campus.lab_resource_platform.booking.service.SharedOccupancyService;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.SharedScheduleResponse;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingListingRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
public class SharedScheduleServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private SharingListingRepository listingRepository;

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private SharedOccupancyService occupancyService;

    @Mock
    private OperatingHoursCapacityService capacityService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private SharedScheduleService scheduleService;

    private Equipment equipment;
    private User ownerAdmin;
    private User regularUser;
    private SharingListing listing;

    @BeforeEach
    void setUp() {
        equipment = new Equipment();
        equipment.setId(10L);
        equipment.setName("Flow Cytometer");
        equipment.setInstitutionId(1L);

        ownerAdmin = new User();
        ownerAdmin.setId(100L);
        ownerAdmin.setEmail("admin@inst1.edu");
        ownerAdmin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        ownerAdmin.setInstitutionId(1L);
        ownerAdmin.setActive(true);

        regularUser = new User();
        regularUser.setId(200L);
        regularUser.setEmail("student@inst2.edu");
        regularUser.setRole(User.Role.RESEARCHER_STUDENT);
        regularUser.setInstitutionId(2L);
        regularUser.setActive(true);

        listing = new SharingListing();
        listing.setId(5L);
        listing.setEquipment(equipment);
        listing.setExternalSharePercentage(new BigDecimal("25.00"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetSharedSchedule_OwnerAdminCanSeePartnerInstitutionName() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
        ownerAdmin.getEmail(),
        null,
        Collections.emptyList()
)
        );
        when(userRepository.findByEmail(ownerAdmin.getEmail())).thenReturn(Optional.of(ownerAdmin));
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));

        SharedOccupancyService.OccupancySlot externalSlot = new SharedOccupancyService.OccupancySlot(
                1L,
                LocalDateTime.of(2026, 11, 10, 10, 0),
                LocalDateTime.of(2026, 11, 10, 12, 0),
                true,
                "BOOKING",
                2L // Partner institution ID
        );

        when(occupancyService.getOccupiedSlots(eq(10L), any(), any()))
                .thenReturn(List.of(externalSlot));
        when(institutionService.getInstitutionName(2L)).thenReturn("State University");
        when(listingRepository.findByEquipmentId(10L)).thenReturn(Optional.of(listing));
        when(capacityService.getWeeklyOperatingHoursCapacity(eq(equipment), any())).thenReturn(4000L);
        when(bookingRepository.findExternalBookingsInPeriod(eq(10L), any(), any(), any())).thenReturn(Collections.emptyList());

        SharedScheduleResponse resp = scheduleService.getSharedSchedule(10L, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

        assertNotNull(resp);
        assertEquals(1, resp.getOccupiedSlots().size());
        SharedScheduleResponse.OccupiedSlot slot = resp.getOccupiedSlots().get(0);
        assertEquals("EXTERNAL", slot.getType());
        assertEquals("State University", slot.getPartnerInstitutionName());
        assertEquals(1000L, resp.getWeeklyExternalShareLimitMinutes()); // 25% of 4000
    }

    @Test
    void testGetSharedSchedule_RegularUserCannotSeePartnerInstitutionName() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        regularUser.getEmail(),
                        null,
                        Collections.emptyList()
                    )
        );
        when(userRepository.findByEmail(regularUser.getEmail())).thenReturn(Optional.of(regularUser));
        when(equipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));

        SharedOccupancyService.OccupancySlot externalSlot = new SharedOccupancyService.OccupancySlot(
                1L,
                LocalDateTime.of(2026, 11, 10, 10, 0),
                LocalDateTime.of(2026, 11, 10, 12, 0),
                true,
                "BOOKING",
                2L
        );

        when(occupancyService.getOccupiedSlots(eq(10L), any(), any()))
                .thenReturn(List.of(externalSlot));
        when(listingRepository.findByEquipmentId(10L)).thenReturn(Optional.of(listing));
        when(capacityService.getWeeklyOperatingHoursCapacity(eq(equipment), any())).thenReturn(4000L);
        when(bookingRepository.findExternalBookingsInPeriod(eq(10L), any(), any(), any())).thenReturn(Collections.emptyList());

        SharedScheduleResponse resp = scheduleService.getSharedSchedule(10L, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

        assertNotNull(resp);
        assertEquals(1, resp.getOccupiedSlots().size());
        SharedScheduleResponse.OccupiedSlot slot = resp.getOccupiedSlots().get(0);
        assertEquals("EXTERNAL", slot.getType());
        // Requirement: Regular users must NOT see partner institution details
        assertNull(slot.getPartnerInstitutionName());
    }
}

