
package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.SharingListingDiscoveryResponse;
import com.campus.lab_resource_platform.sharing.dto.SharingListingRequest;
import com.campus.lab_resource_platform.sharing.dto.SharingListingResponse;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.AccessRequestRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SharingListingServiceTest {

    @Mock
    private SharingListingRepository listingRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private AccessRequestRepository requestRepository;

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private SharingListingService listingService;

    private User adminUser;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@inst1.edu");
        adminUser.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        adminUser.setInstitutionId(100L);
        adminUser.setActive(true);

        equipment = new Equipment();
        equipment.setId(10L);
        equipment.setName("TEM Microscope");
        equipment.setAssetTag("TEM-001");
        equipment.setInstitutionId(100L);
        equipment.setIsShareable(true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        adminUser.getEmail(),
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
    void testCreateListing_Success() {
        when(userRepository.findByEmail(adminUser.getEmail()))
                .thenReturn(Optional.of(adminUser));
        when(equipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipment));
        when(listingRepository.existsByEquipmentId(10L))
                .thenReturn(false);
        when(listingRepository.save(any(SharingListing.class))).thenAnswer(i -> {
            SharingListing sl = i.getArgument(0);
            sl.setId(50L);
            return sl;
        });
        when(institutionService.getInstitutionName(100L))
                .thenReturn("Institution 100");

        SharingListingRequest req = new SharingListingRequest();
        req.setEquipmentId(10L);
        req.setFeePerHour(new BigDecimal("150.00"));
        req.setExternalSharePercentage(new BigDecimal("20.00"));
        req.setMinNoticeHours(24);
        req.setMaxBookingDurationHours(8);
        req.setSharingTerms("External users must follow safety protocols");
        req.setActive(true);

        SharingListingResponse response = listingService.createListing(req);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals(10L, response.getEquipmentId());
        assertEquals(new BigDecimal("150.00"), response.getFeePerHour());
        assertEquals(new BigDecimal("20.00"), response.getExternalSharePercentage());
        assertTrue(response.isActive());
    }

    @Test
    void testCreateListing_FailsWhenNotShareable() {
        equipment.setIsShareable(false);

        when(userRepository.findByEmail(adminUser.getEmail()))
                .thenReturn(Optional.of(adminUser));
        when(equipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipment));

        SharingListingRequest req = new SharingListingRequest();
        req.setEquipmentId(10L);
        req.setFeePerHour(new BigDecimal("100.00"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> listingService.createListing(req)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not marked as shareable"));
    }

    @Test
    void testCreateListing_FailsWhenDuplicateListing() {
        when(userRepository.findByEmail(adminUser.getEmail()))
                .thenReturn(Optional.of(adminUser));
        when(equipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipment));
        when(listingRepository.existsByEquipmentId(10L))
                .thenReturn(true);

        SharingListingRequest req = new SharingListingRequest();
        req.setEquipmentId(10L);
        req.setFeePerHour(new BigDecimal("100.00"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> listingService.createListing(req)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void testCreateListing_FailsForUnauthorizedUser() {
        adminUser.setRole(User.Role.RESEARCHER_STUDENT);

        when(userRepository.findByEmail(adminUser.getEmail()))
                .thenReturn(Optional.of(adminUser));
        when(equipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipment));

        SharingListingRequest req = new SharingListingRequest();
        req.setEquipmentId(10L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> listingService.createListing(req)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testActivateAndDeactivateListing() {
        SharingListing listing = new SharingListing();
        listing.setId(50L);
        listing.setEquipment(equipment);
        listing.setOwnerInstitutionId(100L);
        listing.setActive(true);

        when(userRepository.findByEmail(adminUser.getEmail()))
                .thenReturn(Optional.of(adminUser));
        when(listingRepository.findById(50L))
                .thenReturn(Optional.of(listing));
        when(listingRepository.save(any(SharingListing.class)))
                .thenAnswer(i -> i.getArgument(0));

        SharingListingResponse deactivated =
                listingService.deactivateListing(50L);
        assertFalse(deactivated.isActive());

        SharingListingResponse activated =
                listingService.activateListing(50L);
        assertTrue(activated.isActive());
    }

    @Test
    void testDiscoverListings_ReturnsActiveListingsFromOtherInstitutions() {
        when(userRepository.findByEmail(adminUser.getEmail()))
                .thenReturn(Optional.of(adminUser));

        SharingListing listing = new SharingListing();
        listing.setId(100L);
        listing.setEquipment(equipment);
        listing.setOwnerInstitutionId(200L);
        listing.setActive(true);
        listing.setFeePerHour(new BigDecimal("75.00"));
        listing.setExternalSharePercentage(new BigDecimal("15.00"));

        Page<SharingListing> page = new PageImpl<>(List.of(listing));

        when(listingRepository.discoverListings(
                eq(100L),
                isNull(),
                isNull(),
                isNull(),
                any(PageRequest.class)
        )).thenReturn(page);

        when(institutionService.getInstitutionName(200L))
                .thenReturn("Partner University");

        Page<SharingListingDiscoveryResponse> result =
                listingService.discoverListings(
                        null,
                        null,
                        null,
                        PageRequest.of(0, 10)
                );

        assertEquals(1, result.getTotalElements());

        SharingListingDiscoveryResponse resp = result.getContent().get(0);
        assertEquals(100L, resp.getListingId());
        assertEquals("Partner University", resp.getOwnerInstitutionName());
        assertEquals(new BigDecimal("75.00"), resp.getFeePerHour());
    }
}
