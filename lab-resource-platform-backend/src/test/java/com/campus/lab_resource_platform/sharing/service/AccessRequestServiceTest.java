
package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.AccessRequestCreateDto;
import com.campus.lab_resource_platform.sharing.dto.AccessRequestResponse;
import com.campus.lab_resource_platform.sharing.dto.ReviewRemarksDto;
import com.campus.lab_resource_platform.sharing.entity.*;
import com.campus.lab_resource_platform.sharing.repository.AccessGrantRepository;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccessRequestServiceTest {

    @Mock
    private AccessRequestRepository requestRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private SharingListingRepository listingRepository;

    @Mock
    private SharingAgreementRepository agreementRepository;

    @Mock
    private AccessGrantRepository grantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private AccessRequestService requestService;

    private User requester;
    private User requestingAdmin;
    private User ownerAdmin;
    private Equipment equipment;
    private SharingListing listing;

    @BeforeEach
    void setUp() {
        requester = new User();
        requester.setId(10L);
        requester.setEmail("researcher@inst1.edu");
        requester.setFullName("Alice Researcher");
        requester.setRole(User.Role.RESEARCHER_STUDENT);
        requester.setInstitutionId(1L);
        requester.setDepartmentId(5L);
        requester.setActive(true);

        requestingAdmin = new User();
        requestingAdmin.setId(20L);
        requestingAdmin.setEmail("admin@inst1.edu");
        requestingAdmin.setFullName("Requesting Admin");
        requestingAdmin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        requestingAdmin.setInstitutionId(1L);
        requestingAdmin.setActive(true);

        ownerAdmin = new User();
        ownerAdmin.setId(30L);
        ownerAdmin.setEmail("admin@inst2.edu");
        ownerAdmin.setFullName("Owner Admin");
        ownerAdmin.setRole(User.Role.INSTITUTION_ADMINISTRATOR);
        ownerAdmin.setInstitutionId(2L);
        ownerAdmin.setActive(true);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Spectrometer");
        equipment.setAssetTag("SPEC-01");
        equipment.setInstitutionId(2L);
        equipment.setIsShareable(true);

        listing = new SharingListing();
        listing.setId(50L);
        listing.setEquipment(equipment);
        listing.setOwnerInstitutionId(2L);
        listing.setFeePerHour(new BigDecimal("120.00"));
        listing.setActive(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                )
        );

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
    }

    @Test
    void testCreateRequest_Success() {
        authenticateAs(requester);

        when(equipmentRepository.findById(100L))
                .thenReturn(Optional.of(equipment));
        when(listingRepository.findByEquipmentId(100L))
                .thenReturn(Optional.of(listing));
        when(requestRepository.findOverlappingOpenRequests(
                eq(100L), eq(10L), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(requestRepository.save(any(AccessRequest.class))).thenAnswer(i -> {
            AccessRequest ar = i.getArgument(0);
            ar.setId(1L);
            return ar;
        });

        AccessRequestCreateDto dto = new AccessRequestCreateDto();
        dto.setEquipmentId(100L);
        dto.setPurpose("Biomedical material research collaboration");
        dto.setStartDate(LocalDate.now().plusDays(2));
        dto.setEndDate(LocalDate.now().plusDays(10));

        AccessRequestResponse resp = requestService.createRequest(dto);

        assertNotNull(resp);
        assertEquals(AccessRequestStatus.PENDING, resp.getStatus());
        assertEquals(10L, resp.getRequesterId());
        assertEquals(1L, resp.getRequestingInstitutionId());
        assertEquals(2L, resp.getOwnerInstitutionId());
    }

    @Test
    void testCreateRequest_FailsForSameInstitution() {
        equipment.setInstitutionId(1L);

        authenticateAs(requester);

        when(equipmentRepository.findById(100L))
                .thenReturn(Optional.of(equipment));

        AccessRequestCreateDto dto = new AccessRequestCreateDto();
        dto.setEquipmentId(100L);
        dto.setPurpose("Test");
        dto.setStartDate(LocalDate.now().plusDays(1));
        dto.setEndDate(LocalDate.now().plusDays(2));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> requestService.createRequest(dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("own institution"));
    }

    @Test
    void testCreateRequest_FailsOnOverlappingOpenRequest() {
        authenticateAs(requester);

        when(equipmentRepository.findById(100L))
                .thenReturn(Optional.of(equipment));
        when(listingRepository.findByEquipmentId(100L))
                .thenReturn(Optional.of(listing));

        AccessRequest existing = new AccessRequest();
        existing.setId(2L);

        when(requestRepository.findOverlappingOpenRequests(
                eq(100L), eq(10L), any(), any(), any()))
                .thenReturn(List.of(existing));

        AccessRequestCreateDto dto = new AccessRequestCreateDto();
        dto.setEquipmentId(100L);
        dto.setPurpose("Test overlap");
        dto.setStartDate(LocalDate.now().plusDays(2));
        dto.setEndDate(LocalDate.now().plusDays(5));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> requestService.createRequest(dto)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void testTwoStepApproval_ForwardAndApproveWorkflow() {
        AccessRequest request = new AccessRequest();
        request.setId(500L);
        request.setEquipment(equipment);
        request.setRequester(requester);
        request.setRequestingInstitutionId(1L);
        request.setOwnerInstitutionId(2L);
        request.setPurpose("Research project");
        request.setStartDate(LocalDate.now().plusDays(3));
        request.setEndDate(LocalDate.now().plusDays(7));
        request.setStatus(AccessRequestStatus.PENDING);

        when(requestRepository.findById(500L))
                .thenReturn(Optional.of(request));
        when(requestRepository.save(any(AccessRequest.class)))
                .thenAnswer(i -> i.getArgument(0));

        // Step 1: Forward by Requesting Admin
        authenticateAs(requestingAdmin);

        AccessRequestResponse forwardResp =
                requestService.forwardRequest(
                        500L,
                        new ReviewRemarksDto("Endorsed by university")
                );

        assertEquals(AccessRequestStatus.FORWARDED, forwardResp.getStatus());
        assertEquals(20L, forwardResp.getRequesterReviewedBy());
        assertEquals("Endorsed by university",
                forwardResp.getRequesterReviewRemarks());
        assertNotNull(forwardResp.getRequesterReviewedAt());

        // Step 2: Approve by Owner Admin
        when(listingRepository.findByEquipmentId(100L))
                .thenReturn(Optional.of(listing));

        when(agreementRepository.save(any(SharingAgreement.class)))
                .thenAnswer(i -> {
                    SharingAgreement sa = i.getArgument(0);
                    sa.setId(888L);
                    return sa;
                });

        authenticateAs(ownerAdmin);

        AccessRequestResponse approveResp =
                requestService.approveRequest(
                        500L,
                        new ReviewRemarksDto("Approved for lab use")
                );

        assertEquals(AccessRequestStatus.APPROVED, approveResp.getStatus());
        assertEquals(30L, approveResp.getFinalReviewedBy());
        assertEquals("Approved for lab use",
                approveResp.getFinalReviewRemarks());

        verify(agreementRepository, times(1)).save(argThat(sa ->
                sa.getFeePerHour().equals(new BigDecimal("120.00"))
                        && sa.getOwnerInstitutionId().equals(2L)
                        && sa.getPartnerInstitutionId().equals(1L)
        ));

        verify(grantRepository, times(1)).save(argThat(ag ->
                ag.getUser().getId().equals(10L)
                        && ag.getStatus() == GrantStatus.ACTIVE
        ));
    }

    @Test
    void testReject_RequiresMandatoryRemarks() {
        AccessRequest request = new AccessRequest();
        request.setId(501L);
        request.setRequestingInstitutionId(1L);
        request.setOwnerInstitutionId(2L);
        request.setStatus(AccessRequestStatus.PENDING);

        when(requestRepository.findById(501L))
                .thenReturn(Optional.of(request));

        authenticateAs(requestingAdmin);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> requestService.rejectByRequestingAdmin(
                        501L,
                        new ReviewRemarksDto("")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("mandatory"));
    }

    @Test
    void testWithdrawRequest_SuccessWhilePending() {
        AccessRequest request = new AccessRequest();
        request.setId(502L);
        request.setEquipment(equipment);
        request.setRequester(requester);
        request.setStatus(AccessRequestStatus.PENDING);

        when(requestRepository.findById(502L))
                .thenReturn(Optional.of(request));
        when(requestRepository.save(any(AccessRequest.class)))
                .thenAnswer(i -> i.getArgument(0));

        authenticateAs(requester);

        AccessRequestResponse resp = requestService.withdrawRequest(502L);

        assertEquals(AccessRequestStatus.WITHDRAWN, resp.getStatus());
    }

    @Test
    void testOwnerAdminRequestList_ShowsOnlyForwardedRequests() {
        authenticateAs(ownerAdmin);

        Page<AccessRequest> page = new PageImpl<>(List.of());

        when(requestRepository.findByOwnerInstitutionIdAndStatusInOrderByCreatedAtDesc(
                eq(2L),
                eq(List.of(
                        AccessRequestStatus.FORWARDED,
                        AccessRequestStatus.APPROVED,
                        AccessRequestStatus.REJECTED
                )),
                any()
        )).thenReturn(page);

        Page<AccessRequestResponse> results =
                requestService.getOwnerInstitutionRequests(PageRequest.of(0, 10));

        assertNotNull(results);

        verify(requestRepository).findByOwnerInstitutionIdAndStatusInOrderByCreatedAtDesc(
                eq(2L),
                eq(List.of(
                        AccessRequestStatus.FORWARDED,
                        AccessRequestStatus.APPROVED,
                        AccessRequestStatus.REJECTED
                )),
                any()
        );
    }
}
