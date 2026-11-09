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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class AccessRequestService {

    private final AccessRequestRepository requestRepository;
    private final EquipmentRepository equipmentRepository;
    private final SharingListingRepository listingRepository;
    private final SharingAgreementRepository agreementRepository;
    private final AccessGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final InstitutionService institutionService;

    public AccessRequestService(
            AccessRequestRepository requestRepository,
            EquipmentRepository equipmentRepository,
            SharingListingRepository listingRepository,
            SharingAgreementRepository agreementRepository,
            AccessGrantRepository grantRepository,
            UserRepository userRepository,
            InstitutionService institutionService) {
        this.requestRepository = requestRepository;
        this.equipmentRepository = equipmentRepository;
        this.listingRepository = listingRepository;
        this.agreementRepository = agreementRepository;
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
        this.institutionService = institutionService;
    }

    @Transactional
    public synchronized AccessRequestResponse createRequest(AccessRequestCreateDto dto) {
        User requester = getAuthenticatedUser();

        // Role check: Researcher, Student or Department Head
        Set<User.Role> allowedRoles = Set.of(
                User.Role.RESEARCHER_STUDENT,
                User.Role.DEPARTMENT_HEAD,
                User.Role.LAB_TECHNICIAN
        );
        if (!allowedRoles.contains(requester.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only researchers, students, or department heads can submit access requests");
        }

        // Must belong to an institution
        if (requester.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must belong to an institution to submit an inter-institution request");
        }

        Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + dto.getEquipmentId()));

        // Equipment must be owned by another institution
        if (equipment.getInstitutionId().equals(requester.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot submit inter-institution access request for equipment owned by your own institution");
        }

        // Listing check
        SharingListing listing = listingRepository.findByEquipmentId(equipment.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment does not have a sharing listing"));

        if (!listing.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment sharing listing is currently inactive");
        }

        // Date validation
        LocalDate start = dto.getStartDate();
        LocalDate end = dto.getEndDate();
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be in the past");
        }

        // Prevent duplicate open requests for the same equipment with overlapping dates
        List<AccessRequestStatus> openStatuses = List.of(
                AccessRequestStatus.PENDING,
                AccessRequestStatus.FORWARDED,
                AccessRequestStatus.APPROVED
        );

        List<AccessRequest> overlapping = requestRepository.findOverlappingOpenRequests(
                equipment.getId(), requester.getId(), start, end, openStatuses);

        if (!overlapping.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have an open or approved request for this equipment with overlapping dates");
        }

        AccessRequest request = new AccessRequest();
        request.setEquipment(equipment);
        request.setRequester(requester);
        request.setRequestingInstitutionId(requester.getInstitutionId());
        request.setOwnerInstitutionId(equipment.getInstitutionId());
        request.setDepartmentId(requester.getDepartmentId());
        request.setPurpose(dto.getPurpose().trim());
        request.setStartDate(start);
        request.setEndDate(end);
        request.setStatus(AccessRequestStatus.PENDING);

        AccessRequest saved = requestRepository.save(request);
        return toResponse(saved);
    }

    /**
     * Step 1: Forward request (by Requesting Institution Administrator).
     */
    @Transactional
    public synchronized AccessRequestResponse forwardRequest(Long requestId, ReviewRemarksDto dto) {
        AccessRequest request = findRequestWithLock(requestId);
        User reviewer = getAuthenticatedUser();

        authorizeRequestingAdmin(request.getRequestingInstitutionId(), reviewer);

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING requests can be forwarded. Current status: " + request.getStatus());
        }

        request.setStatus(AccessRequestStatus.FORWARDED);
        request.setRequesterReviewedBy(reviewer.getId());
        request.setRequesterReviewedByName(reviewer.getFullName());
        request.setRequesterReviewedAt(Instant.now());
        if (dto != null && dto.getRemarks() != null) {
            request.setRequesterReviewRemarks(dto.getRemarks().trim());
        }

        AccessRequest saved = requestRepository.save(request);
        return toResponse(saved);
    }

    /**
     * Step 1: Reject request (by Requesting Institution Administrator).
     * Mandatory remarks required!
     */
    @Transactional
    public synchronized AccessRequestResponse rejectByRequestingAdmin(Long requestId, ReviewRemarksDto dto) {
        AccessRequest request = findRequestWithLock(requestId);
        User reviewer = getAuthenticatedUser();

        authorizeRequestingAdmin(request.getRequestingInstitutionId(), reviewer);

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING requests can be rejected at this stage");
        }

        if (dto == null || dto.getRemarks() == null || dto.getRemarks().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection remarks are mandatory");
        }

        request.setStatus(AccessRequestStatus.REJECTED);
        request.setRequesterReviewedBy(reviewer.getId());
        request.setRequesterReviewedByName(reviewer.getFullName());
        request.setRequesterReviewedAt(Instant.now());
        request.setRequesterReviewRemarks(dto.getRemarks().trim());

        AccessRequest saved = requestRepository.save(request);
        return toResponse(saved);
    }

    /**
     * Step 2: Approve request (by Owner Institution Administrator).
     * Creates Agreement and AccessGrant in a single transaction.
     */
    @Transactional
    public synchronized AccessRequestResponse approveRequest(Long requestId, ReviewRemarksDto dto) {
        AccessRequest request = findRequestWithLock(requestId);
        User reviewer = getAuthenticatedUser();

        authorizeOwnerAdmin(request.getOwnerInstitutionId(), reviewer);

        if (request.getStatus() != AccessRequestStatus.FORWARDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FORWARDED requests can be approved. Current status: " + request.getStatus());
        }

        // Fetch listing to copy fee per hour
        SharingListing listing = listingRepository.findByEquipmentId(request.getEquipment().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment does not have an active sharing listing"));

        Instant now = Instant.now();

        // 1. Update request status to APPROVED and record final reviewer details
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setFinalReviewedBy(reviewer.getId());
        request.setFinalReviewedByName(reviewer.getFullName());
        request.setFinalReviewedAt(now);
        if (dto != null && dto.getRemarks() != null) {
            request.setFinalReviewRemarks(dto.getRemarks().trim());
        }
        AccessRequest savedRequest = requestRepository.save(request);

        // 2. Create Sharing Agreement (copying listing feePerHour)
        SharingAgreement agreement = new SharingAgreement();
        agreement.setAccessRequest(savedRequest);
        agreement.setEquipment(request.getEquipment());
        agreement.setOwnerInstitutionId(request.getOwnerInstitutionId());
        agreement.setPartnerInstitutionId(request.getRequestingInstitutionId());
        agreement.setStartDate(request.getStartDate());
        agreement.setEndDate(request.getEndDate());
        agreement.setFeePerHour(listing.getFeePerHour());
        agreement.setStatus(AgreementStatus.ACTIVE);
        agreement.setApprovedByUserId(reviewer.getId());
        agreement.setApprovedAt(now);

        SharingAgreement savedAgreement = agreementRepository.save(agreement);

        // 3. Create Access Grant for the requester
        AccessGrant grant = new AccessGrant();
        grant.setAgreement(savedAgreement);
        grant.setUser(request.getRequester());
        grant.setStatus(GrantStatus.ACTIVE);
        grant.setGrantedAt(now);

        grantRepository.save(grant);

        return toResponse(savedRequest);
    }

    /**
     * Step 2: Reject request (by Owner Institution Administrator).
     * Mandatory remarks required!
     */
    @Transactional
    public synchronized AccessRequestResponse rejectByOwnerAdmin(Long requestId, ReviewRemarksDto dto) {
        AccessRequest request = findRequestWithLock(requestId);
        User reviewer = getAuthenticatedUser();

        authorizeOwnerAdmin(request.getOwnerInstitutionId(), reviewer);

        if (request.getStatus() != AccessRequestStatus.FORWARDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FORWARDED requests can be rejected by the owner administrator");
        }

        if (dto == null || dto.getRemarks() == null || dto.getRemarks().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection remarks are mandatory");
        }

        request.setStatus(AccessRequestStatus.REJECTED);
        request.setFinalReviewedBy(reviewer.getId());
        request.setFinalReviewedByName(reviewer.getFullName());
        request.setFinalReviewedAt(Instant.now());
        request.setFinalReviewRemarks(dto.getRemarks().trim());

        AccessRequest saved = requestRepository.save(request);
        return toResponse(saved);
    }

    /**
     * Withdraw request: Requester can withdraw while in PENDING or FORWARDED status.
     */
    @Transactional
    public synchronized AccessRequestResponse withdrawRequest(Long requestId) {
        AccessRequest request = findRequestWithLock(requestId);
        User user = getAuthenticatedUser();

        if (!request.getRequester().getId().equals(user.getId()) && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can withdraw their request");
        }

        if (request.getStatus() != AccessRequestStatus.PENDING && request.getStatus() != AccessRequestStatus.FORWARDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request can only be withdrawn while in PENDING or FORWARDED status");
        }

        request.setStatus(AccessRequestStatus.WITHDRAWN);
        AccessRequest saved = requestRepository.save(request);
        return toResponse(saved);
    }

    // Separate request lists

    @Transactional(readOnly = true)
    public Page<AccessRequestResponse> getMyRequests(Pageable pageable) {
        User user = getAuthenticatedUser();
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AccessRequestResponse> getDepartmentRequests(Pageable pageable) {
        User user = getAuthenticatedUser();
        if (user.getRole() != User.Role.DEPARTMENT_HEAD && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only department heads can view department access requests");
        }
        if (user.getDepartmentId() == null || user.getInstitutionId() == null) {
            return Page.empty(pageable);
        }
        return requestRepository.findByDepartmentIdAndRequestingInstitutionIdOrderByCreatedAtDesc(
                user.getDepartmentId(), user.getInstitutionId(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AccessRequestResponse> getRequestingInstitutionRequests(Pageable pageable) {
        User user = getAuthenticatedUser();
        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only institution administrators can view requesting institution requests");
        }
        if (user.getInstitutionId() == null) {
            return Page.empty(pageable);
        }
        return requestRepository.findByRequestingInstitutionIdOrderByCreatedAtDesc(user.getInstitutionId(), pageable)
                .map(this::toResponse);
    }

    /**
     * Owner institution admin requests:
     * Requirement: The owner's Institution Administrator must see a request only after it has been forwarded!
     */
    @Transactional(readOnly = true)
    public Page<AccessRequestResponse> getOwnerInstitutionRequests(Pageable pageable) {
        User user = getAuthenticatedUser();
        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only institution administrators can view owner institution requests");
        }
        if (user.getInstitutionId() == null) {
            return Page.empty(pageable);
        }

        // Visible only after forwarded: FORWARDED, APPROVED, REJECTED
        List<AccessRequestStatus> visibleStatuses = List.of(
                AccessRequestStatus.FORWARDED,
                AccessRequestStatus.APPROVED,
                AccessRequestStatus.REJECTED
        );

        return requestRepository.findByOwnerInstitutionIdAndStatusInOrderByCreatedAtDesc(
                user.getInstitutionId(), visibleStatuses, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AccessRequestResponse getRequestById(Long id) {
        AccessRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found: " + id));

        User user = getAuthenticatedUser();
        authorizeViewRequest(request, user);

        return toResponse(request);
    }

    private void authorizeViewRequest(AccessRequest request, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }

        // Requester
        if (request.getRequester().getId().equals(user.getId())) {
            return;
        }

        // Department Head of requester's department
        if (user.getRole() == User.Role.DEPARTMENT_HEAD
                && user.getDepartmentId() != null
                && user.getDepartmentId().equals(request.getDepartmentId())) {
            return;
        }

        // Requesting institution administrator
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && user.getInstitutionId() != null
                && user.getInstitutionId().equals(request.getRequestingInstitutionId())) {
            return;
        }

        // Owner institution administrator - only after forwarded!
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && user.getInstitutionId() != null
                && user.getInstitutionId().equals(request.getOwnerInstitutionId())) {
            if (request.getStatus() == AccessRequestStatus.FORWARDED
                    || request.getStatus() == AccessRequestStatus.APPROVED
                    || request.getStatus() == AccessRequestStatus.REJECTED) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view this request");
    }

    private AccessRequest findRequestWithLock(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access request not found: " + id));
    }

    private void authorizeRequestingAdmin(Long requestingInstitutionId, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && user.getInstitutionId() != null
                && user.getInstitutionId().equals(requestingInstitutionId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requesting institution administrator can review this request at this stage");
    }

    private void authorizeOwnerAdmin(Long ownerInstitutionId, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && user.getInstitutionId() != null
                && user.getInstitutionId().equals(ownerInstitutionId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner institution administrator can review this forwarded request");
    }

    public AccessRequestResponse toResponse(AccessRequest r) {
        AccessRequestResponse resp = new AccessRequestResponse();
        resp.setId(r.getId());
        resp.setEquipmentId(r.getEquipment().getId());
        resp.setEquipmentName(r.getEquipment().getName());
        resp.setAssetTag(r.getEquipment().getAssetTag());

        resp.setRequesterId(r.getRequester().getId());
        resp.setRequesterName(r.getRequester().getFullName());
        resp.setRequesterEmail(r.getRequester().getEmail());

        resp.setRequestingInstitutionId(r.getRequestingInstitutionId());
        resp.setRequestingInstitutionName(institutionService.getInstitutionName(r.getRequestingInstitutionId()));

        resp.setOwnerInstitutionId(r.getOwnerInstitutionId());
        resp.setOwnerInstitutionName(institutionService.getInstitutionName(r.getOwnerInstitutionId()));

        resp.setDepartmentId(r.getDepartmentId());
        resp.setPurpose(r.getPurpose());
        resp.setStartDate(r.getStartDate());
        resp.setEndDate(r.getEndDate());
        resp.setStatus(r.getStatus());

        resp.setRequesterReviewedBy(r.getRequesterReviewedBy());
        resp.setRequesterReviewedByName(r.getRequesterReviewedByName());
        resp.setRequesterReviewedAt(r.getRequesterReviewedAt());
        resp.setRequesterReviewRemarks(r.getRequesterReviewRemarks());

        resp.setFinalReviewedBy(r.getFinalReviewedBy());
        resp.setFinalReviewedByName(r.getFinalReviewedByName());
        resp.setFinalReviewedAt(r.getFinalReviewedAt());
        resp.setFinalReviewRemarks(r.getFinalReviewRemarks());

        resp.setCreatedAt(r.getCreatedAt());
        resp.setUpdatedAt(r.getUpdatedAt());
        return resp;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is inactive");
        }
        return user;
    }
}

