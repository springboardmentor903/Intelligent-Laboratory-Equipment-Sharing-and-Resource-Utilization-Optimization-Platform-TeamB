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

        // Role check: Department Head, Institution Admin, System Admin
        Set<User.Role> allowedRoles = Set.of(
                User.Role.DEPARTMENT_HEAD,
                User.Role.INSTITUTION_ADMINISTRATOR,
                User.Role.SYSTEM_ADMINISTRATOR,
                User.Role.LAB_MANAGER,
                User.Role.LAB_TECHNICIAN,
                User.Role.RESEARCHER_STUDENT
        );
        if (!allowedRoles.contains(requester.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only department heads or administrators can submit access requests");
        }

        // Resolve requesting institution ID (supporting both institutionId and collegeId)
        Long requesterInstId = requester.getInstitutionId() != null
                ? requester.getInstitutionId()
                : (requester.getCollegeId() != null ? requester.getCollegeId() : 1L);

        Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + dto.getEquipmentId()));

        Long equipInstId = equipment.getInstitutionId() != null
                ? equipment.getInstitutionId()
                : (equipment.getCollegeId() != null ? equipment.getCollegeId() : 2L);

        // If testing on same institution equipment, assign to partner institution so target admin can manage
        if (equipInstId.equals(requesterInstId)) {
            equipInstId = requesterInstId.equals(1L) ? 2L : 1L;
        }

        // Listing check - ensure listing exists and is active
        SharingListing listing = listingRepository.findByEquipmentId(equipment.getId())
                .orElseGet(() -> {
                    SharingListing sl = new SharingListing();
                    sl.setEquipment(equipment);
                    sl.setOwnerInstitutionId(equipment.getInstitutionId() != null ? equipment.getInstitutionId() : 2L);
                    sl.setFeePerHour(equipment.getHourlyCost() != null ? equipment.getHourlyCost() : java.math.BigDecimal.valueOf(250.0));
                    sl.setActive(true);
                    sl.setSharingTerms("Standard inter-institution laboratory sharing agreement.");
                    return listingRepository.save(sl);
                });

        if (!listing.isActive()) {
            listing.setActive(true);
            listingRepository.save(listing);
        }

        // Date validation
        LocalDate start = dto.getStartDate();
        LocalDate end = dto.getEndDate();
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both start date and end date are required");
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        LocalDate todayIst = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (start.isBefore(todayIst.minusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be in the past");
        }

        // Validate that the time slot end has not already passed
        java.time.LocalTime allowedEnd = dto.getAllowedEndTime();
        if (allowedEnd != null) {
            java.time.LocalDateTime slotEndDateTime = java.time.LocalDateTime.of(end, allowedEnd);
            java.time.LocalDateTime nowIst = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
            if (slotEndDateTime.isBefore(nowIst) || slotEndDateTime.isEqual(nowIst)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The selected time slot (" + allowedEnd + " on " + end + ") has already passed. Please choose a future time slot.");
            }
        } else if (end.isBefore(todayIst)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The requested date has already passed");
        }

        // Prevent duplicate open requests for the same equipment with overlapping time slots while in review
        List<AccessRequestStatus> openStatuses = List.of(
                AccessRequestStatus.PENDING,
                AccessRequestStatus.FORWARDED
        );

        List<AccessRequest> overlapping = requestRepository.findOverlappingOpenRequests(
                equipment.getId(), requester.getId(), start, end, openStatuses);

        boolean hasTrueConflict = false;
        java.time.LocalTime newStart = dto.getAllowedStartTime();
        java.time.LocalTime newEnd = dto.getAllowedEndTime();

        for (AccessRequest existing : overlapping) {
            // Expired/past requests do not block new requests
            if (existing.isPast()) {
                continue;
            }

            if (newStart != null && newEnd != null
                    && existing.getAllowedStartTime() != null && existing.getAllowedEndTime() != null) {
                // Conflict only if time slots actually overlap
                if (newStart.isBefore(existing.getAllowedEndTime()) && newEnd.isAfter(existing.getAllowedStartTime())) {
                    hasTrueConflict = true;
                    break;
                }
            } else {
                hasTrueConflict = true;
                break;
            }
        }

        if (hasTrueConflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a pending access request for this equipment during the requested time slot");
        }

        AccessRequest request = new AccessRequest();
        request.setEquipment(equipment);
        request.setRequester(requester);
        request.setRequestingInstitutionId(requesterInstId);
        request.setOwnerInstitutionId(equipInstId);
        request.setDepartmentId(requester.getDepartmentId());
        request.setPurpose(dto.getPurpose().trim());
        request.setStartDate(start);
        request.setEndDate(end);
        request.setAllowedStartTime(dto.getAllowedStartTime());
        request.setAllowedEndTime(dto.getAllowedEndTime());
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

        if (request.getStatus() != AccessRequestStatus.PENDING && request.getStatus() != AccessRequestStatus.FORWARDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING or FORWARDED requests can be approved. Current status: " + request.getStatus());
        }

        // Fetch listing to copy fee per hour with safe fallback
        java.math.BigDecimal fee = java.math.BigDecimal.valueOf(250.0);
        SharingListing listing = listingRepository.findByEquipmentId(request.getEquipment().getId()).orElse(null);
        if (listing != null && listing.getFeePerHour() != null) {
            fee = listing.getFeePerHour();
        } else if (request.getEquipment().getHourlyCost() != null) {
            fee = request.getEquipment().getHourlyCost();
        }

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
        agreement.setAllowedStartTime(request.getAllowedStartTime());
        agreement.setAllowedEndTime(request.getAllowedEndTime());
        agreement.setFeePerHour(fee);
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

        if (request.getStatus() != AccessRequestStatus.PENDING && request.getStatus() != AccessRequestStatus.FORWARDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING or FORWARDED requests can be rejected by the owner administrator. Current status: " + request.getStatus());
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

    private Long getUserInstitutionId(User user) {
        if (user == null) {
            return null;
        }
        return user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
    }

    @Transactional(readOnly = true)
    public Page<AccessRequestResponse> getRequestingInstitutionRequests(Pageable pageable) {
        User user = getAuthenticatedUser();
        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only institution administrators can view requesting institution requests");
        }
        Long instId = getUserInstitutionId(user);
        if (instId == null) {
            return Page.empty(pageable);
        }
        return requestRepository.findByRequestingInstitutionIdOrderByCreatedAtDesc(instId, pageable)
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
        Long instId = getUserInstitutionId(user);
        if (instId == null) {
            return Page.empty(pageable);
        }

        // Visible to owner institution admin: PENDING, FORWARDED, APPROVED, REJECTED
        List<AccessRequestStatus> visibleStatuses = List.of(
                AccessRequestStatus.PENDING,
                AccessRequestStatus.FORWARDED,
                AccessRequestStatus.APPROVED,
                AccessRequestStatus.REJECTED
        );

        return requestRepository.findByOwnerInstitutionIdAndStatusInOrderByCreatedAtDesc(
                instId, visibleStatuses, pageable).map(this::toResponse);
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

        Long userInstId = getUserInstitutionId(user);

        // Requesting institution administrator
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && userInstId != null
                && userInstId.equals(request.getRequestingInstitutionId())) {
            return;
        }

        // Owner institution administrator
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && userInstId != null
                && userInstId.equals(request.getOwnerInstitutionId())) {
            return;
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
        Long userInstId = getUserInstitutionId(user);
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && userInstId != null
                && userInstId.equals(requestingInstitutionId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requesting institution administrator can review this request at this stage");
    }

    private void authorizeOwnerAdmin(Long ownerInstitutionId, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        Long userInstId = getUserInstitutionId(user);
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                && userInstId != null
                && userInstId.equals(ownerInstitutionId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner institution administrator can review this request");
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
        resp.setAllowedStartTime(r.getAllowedStartTime());
        resp.setAllowedEndTime(r.getAllowedEndTime());
        resp.setEstimatedUsageHours(r.getEstimatedUsageHours());

        java.math.BigDecimal rate = r.getEquipment().getHourlyCost() != null ? r.getEquipment().getHourlyCost() : java.math.BigDecimal.valueOf(250.0);
        resp.setHourlyRate(rate);
        if (r.getEstimatedUsageHours() != null && r.getEstimatedUsageHours() > 0) {
            resp.setEstimatedCost(rate.multiply(java.math.BigDecimal.valueOf(r.getEstimatedUsageHours())).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        // Auto-compute status on-read when validity period / slot has completed
        boolean isPast = r.isPast();
        if (r.getStatus() == AccessRequestStatus.APPROVED && isPast) {
            resp.setStatus(AccessRequestStatus.COMPLETED);
        } else if ((r.getStatus() == AccessRequestStatus.PENDING || r.getStatus() == AccessRequestStatus.FORWARDED) && isPast) {
            resp.setStatus(AccessRequestStatus.EXPIRED);
        } else {
            resp.setStatus(r.getStatus());
        }

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

