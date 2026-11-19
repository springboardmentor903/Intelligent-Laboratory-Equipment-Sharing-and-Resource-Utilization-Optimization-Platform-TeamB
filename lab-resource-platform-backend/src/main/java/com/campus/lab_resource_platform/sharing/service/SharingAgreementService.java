package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class SharingAgreementService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final SharingAgreementRepository agreementRepository;
    private final AccessGrantRepository grantRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final InstitutionService institutionService;
    private final ApplicationEventPublisher eventPublisher;

    public SharingAgreementService(
            SharingAgreementRepository agreementRepository,
            AccessGrantRepository grantRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            InstitutionService institutionService,
            ApplicationEventPublisher eventPublisher) {
        this.agreementRepository = agreementRepository;
        this.grantRepository = grantRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.institutionService = institutionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Page<SharingAgreementResponse> getAgreements(Pageable pageable) {
        syncExpiredAgreements();

        User user = getAuthenticatedUser();
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return agreementRepository.findAll(pageable).map(this::toResponse);
        }

        Long instId = getUserInstitutionId(user);
        if (instId == null) {
            return Page.empty(pageable);
        }

        return agreementRepository.findByOwnerInstitutionIdOrPartnerInstitutionId(
                instId, instId, pageable).map(this::toResponse);
    }

    @Transactional
    public void syncExpiredAgreements() {
        try {
            List<SharingAgreement> activeList = agreementRepository.findAllByStatus(AgreementStatus.ACTIVE);
            boolean changed = false;
            for (SharingAgreement sa : activeList) {
                if (sa.isExpired()) {
                    sa.setStatus(AgreementStatus.EXPIRED);
                    changed = true;
                    if (sa.getGrants() != null) {
                        for (AccessGrant g : sa.getGrants()) {
                            if (g.getStatus() == GrantStatus.ACTIVE) {
                                g.setStatus(GrantStatus.REVOKED);
                                g.setRevocationReason("Agreement validity period completed");
                            }
                        }
                    }
                }
            }
            if (changed) {
                agreementRepository.saveAll(activeList);
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public SharingAgreementResponse getAgreementById(Long id) {
        SharingAgreement agreement = agreementRepository.findWithGrantsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agreement not found: " + id));

        User user = getAuthenticatedUser();
        authorizeViewAgreement(agreement, user);

        return toResponse(agreement);
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> getGrantsForAgreement(Long agreementId) {
        SharingAgreement agreement = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agreement not found: " + agreementId));

        User user = getAuthenticatedUser();
        authorizeViewAgreement(agreement, user);

        return grantRepository.findByAgreementId(agreementId).stream()
                .map(this::toGrantResponse)
                .toList();
    }

    /**
     * Revoke agreement:
     * - Only equipment owner's Institution Administrator or System Administrator can revoke.
     * - Requires mandatory reason.
     * - Cancels affected future bookings that have not started and are in PENDING_APPROVAL or CONFIRMED status.
     * - Publishes released-time event so waitlist process can be triggered.
     * - Bookings already in use must continue until their scheduled end time.
     */
    @Transactional
    public SharingAgreementResponse revokeAgreement(Long agreementId, RevokeRequestDto dto) {
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revocation reason is mandatory");
        }

        SharingAgreement agreement = agreementRepository.findWithGrantsById(agreementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agreement not found: " + agreementId));

        User user = getAuthenticatedUser();
        authorizeRevokeAgreement(agreement, user);

        if (agreement.getStatus() == AgreementStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement is already revoked");
        }
        if (agreement.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement has already expired and cannot be revoked");
        }

        Instant now = Instant.now();
        agreement.setStatus(AgreementStatus.REVOKED);
        agreement.setRevokedByUserId(user.getId());
        agreement.setRevokedAt(now);
        agreement.setRevocationReason(dto.getReason().trim());

        // Revoke associated grants
        for (AccessGrant grant : agreement.getGrants()) {
            if (grant.getStatus() == GrantStatus.ACTIVE) {
                grant.setStatus(GrantStatus.REVOKED);
                grant.setRevokedByUserId(user.getId());
                grant.setRevokedAt(now);
                grant.setRevocationReason("Agreement revoked: " + dto.getReason().trim());
            }
        }

        agreementRepository.save(agreement);

        // Cancel future bookings that have not started and are in PENDING_APPROVAL or CONFIRMED status
        Instant currentDateTime = Instant.now();
        Set<BookingStatus> cancelableStatuses = Set.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED
        );

        List<Booking> futureBookings = bookingRepository.findBySharingAgreementIdAndStartTimeAfterAndStatusIn(
                agreement.getId(), currentDateTime, cancelableStatuses);

        for (Booking b : futureBookings) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancellationReason("Sharing agreement revoked: " + dto.getReason().trim());
            bookingRepository.save(b);

            // Publish released-time event
            eventPublisher.publishEvent(new ReleasedTimeEvent(
                    this,
                    b.getEquipment().getId(),
                    b.getId(),
                    LocalDateTime.ofInstant(b.getStartTime(), IST_ZONE),
                    LocalDateTime.ofInstant(b.getEndTime(), IST_ZONE),
                    b.getCancellationReason()
            ));
        }

        return toResponse(agreement);
    }

    /**
     * Revoke individual user's grant:
     * - Only equipment owner's Institution Administrator or System Administrator.
     * - Requires mandatory reason.
     * - Cancels affected future bookings for that user.
     * - Bookings already in use continue until scheduled end time.
     */
    @Transactional
    public AccessGrantResponse revokeGrant(Long grantId, RevokeRequestDto dto) {
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revocation reason is mandatory");
        }

        AccessGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grant not found: " + grantId));

        User user = getAuthenticatedUser();
        authorizeRevokeGrant(grant, user);

        if (grant.getStatus() == GrantStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grant is already revoked");
        }

        Instant now = Instant.now();
        grant.setStatus(GrantStatus.REVOKED);
        grant.setRevokedByUserId(user.getId());
        grant.setRevokedAt(now);
        grant.setRevocationReason(dto.getReason().trim());

        AccessGrant savedGrant = grantRepository.save(grant);

        // Cancel future bookings of this user under this agreement
        Instant currentDateTime = Instant.now();
        Set<BookingStatus> cancelableStatuses = Set.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED
        );

        List<Booking> futureBookings = bookingRepository.findBySharingAgreementIdAndUserIdAndStartTimeAfterAndStatusIn(
                grant.getAgreement().getId(), grant.getUser().getId(), currentDateTime, cancelableStatuses);

        for (Booking b : futureBookings) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancellationReason("Access grant revoked: " + dto.getReason().trim());
            bookingRepository.save(b);

            eventPublisher.publishEvent(new ReleasedTimeEvent(
                    this,
                    b.getEquipment().getId(),
                    b.getId(),
                    LocalDateTime.ofInstant(b.getStartTime(), IST_ZONE),
                    LocalDateTime.ofInstant(b.getEndTime(), IST_ZONE),
                    b.getCancellationReason()
            ));
        }

        return toGrantResponse(savedGrant);
    }

    private void authorizeViewAgreement(SharingAgreement agreement, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }

        Long userInstId = getUserInstitutionId(user);
        if (userInstId != null
                && (userInstId.equals(agreement.getOwnerInstitutionId())
                || userInstId.equals(agreement.getPartnerInstitutionId()))) {
            return;
        }

        // Grantee
        boolean isGrantee = grantRepository.findByAgreementIdAndUserId(agreement.getId(), user.getId()).isPresent();
        if (isGrantee) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view this agreement");
    }

    private void authorizeRevokeAgreement(SharingAgreement agreement, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }

        Long userInstId = getUserInstitutionId(user);
        boolean isOwnerOrPartnerAdminOrHead = (
                user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR ||
                user.getRole() == User.Role.DEPARTMENT_HEAD
        ) && userInstId != null && (
                userInstId.equals(agreement.getOwnerInstitutionId()) ||
                userInstId.equals(agreement.getPartnerInstitutionId())
        );

        if (isOwnerOrPartnerAdminOrHead) {
            return;
        }

        if (agreement.getAccessRequest() != null
                && agreement.getAccessRequest().getRequester() != null
                && agreement.getAccessRequest().getRequester().getId().equals(user.getId())) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only authorized administrators or department heads involved in this agreement can revoke it");
    }

    private void authorizeRevokeGrant(AccessGrant grant, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        Long userInstId = getUserInstitutionId(user);
        Long ownerInstId = grant.getAgreement() != null ? grant.getAgreement().getOwnerInstitutionId() : null;
        Long partnerInstId = grant.getAgreement() != null ? grant.getAgreement().getPartnerInstitutionId() : null;

        boolean isAuthorized = (
                user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR ||
                user.getRole() == User.Role.DEPARTMENT_HEAD
        ) && userInstId != null && (
                userInstId.equals(ownerInstId) || userInstId.equals(partnerInstId)
        );

        if (isAuthorized) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only authorized administrators or department heads can revoke grants");
    }

    /**
     * Returns the institution ID for the user, falling back from collegeId to institutionId.
     * This handles the case where a user has college_id set but institution_id is null (or vice versa).
     */
    private Long getUserInstitutionId(User user) {
        if (user == null) return null;
        return user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
    }

    public SharingAgreementResponse toResponse(SharingAgreement sa) {
        SharingAgreementResponse resp = new SharingAgreementResponse();
        resp.setId(sa.getId());
        if (sa.getAccessRequest() != null) {
            resp.setAccessRequestId(sa.getAccessRequest().getId());
        }

        resp.setEquipmentId(sa.getEquipment().getId());
        resp.setEquipmentName(sa.getEquipment().getName());
        resp.setAssetTag(sa.getEquipment().getAssetTag());

        resp.setOwnerInstitutionId(sa.getOwnerInstitutionId());
        resp.setOwnerInstitutionName(institutionService.getInstitutionName(sa.getOwnerInstitutionId()));

        resp.setPartnerInstitutionId(sa.getPartnerInstitutionId());
        resp.setPartnerInstitutionName(institutionService.getInstitutionName(sa.getPartnerInstitutionId()));

        resp.setStartDate(sa.getStartDate());
        resp.setEndDate(sa.getEndDate());
        resp.setAllowedStartTime(sa.getAllowedStartTime());
        resp.setAllowedEndTime(sa.getAllowedEndTime());

        resp.setFeePerHour(sa.getFeePerHour());
        // Auto-compute EXPIRED status on-read without touching the DB record
        boolean expired = sa.isExpired();
        if (sa.getStatus() == AgreementStatus.ACTIVE && expired) {
            resp.setStatus(AgreementStatus.EXPIRED);
        } else {
            resp.setStatus(sa.getStatus());
        }
        resp.setExpired(expired);

        resp.setApprovedByUserId(sa.getApprovedByUserId());
        resp.setApprovedAt(sa.getApprovedAt());

        resp.setRevokedByUserId(sa.getRevokedByUserId());
        resp.setRevokedAt(sa.getRevokedAt());
        resp.setRevocationReason(sa.getRevocationReason());

        if (sa.getGrants() != null) {
            resp.setGrants(sa.getGrants().stream().map(this::toGrantResponse).toList());
        }

        resp.setCreatedAt(sa.getCreatedAt());
        resp.setUpdatedAt(sa.getUpdatedAt());
        return resp;
    }

    public AccessGrantResponse toGrantResponse(AccessGrant ag) {
        AccessGrantResponse resp = new AccessGrantResponse();
        resp.setId(ag.getId());
        resp.setAgreementId(ag.getAgreement() != null ? ag.getAgreement().getId() : null);
        resp.setUserId(ag.getUser().getId());
        resp.setUserName(ag.getUser().getFullName());
        resp.setUserEmail(ag.getUser().getEmail());
        resp.setStatus(ag.getStatus());
        resp.setGrantedAt(ag.getGrantedAt());
        resp.setRevokedByUserId(ag.getRevokedByUserId());
        resp.setRevokedAt(ag.getRevokedAt());
        resp.setRevocationReason(ag.getRevocationReason());
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
