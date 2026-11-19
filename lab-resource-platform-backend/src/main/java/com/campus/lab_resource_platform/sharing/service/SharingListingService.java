package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.SharingListingDiscoveryResponse;
import com.campus.lab_resource_platform.sharing.dto.SharingListingRequest;
import com.campus.lab_resource_platform.sharing.dto.SharingListingResponse;
import com.campus.lab_resource_platform.sharing.entity.AccessRequest;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.AccessRequestRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingListingRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SharingListingService {

    private final SharingListingRepository listingRepository;
    private final EquipmentRepository equipmentRepository;
    private final AccessRequestRepository requestRepository;
    private final SharingAgreementRepository agreementRepository;
    private final UserRepository userRepository;
    private final InstitutionService institutionService;

    public SharingListingService(
            SharingListingRepository listingRepository,
            EquipmentRepository equipmentRepository,
            AccessRequestRepository requestRepository,
            SharingAgreementRepository agreementRepository,
            UserRepository userRepository,
            InstitutionService institutionService) {
        this.listingRepository = listingRepository;
        this.equipmentRepository = equipmentRepository;
        this.requestRepository = requestRepository;
        this.agreementRepository = agreementRepository;
        this.userRepository = userRepository;
        this.institutionService = institutionService;
    }

    @Transactional
    public SharingListingResponse createListing(SharingListingRequest request) {
        Equipment equipment = equipmentRepository.findById(request.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        User user = getAuthenticatedUser();
        authorizeListingManagement(equipment.getInstitutionId(), user);

        // Requirement: Equipment must be marked as shareable before a listing can be created
        if (!Boolean.TRUE.equals(equipment.getIsShareable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment is not marked as shareable");
        }

        // Requirement: Each equipment can have a maximum of one sharing listing
        if (listingRepository.existsByEquipmentId(equipment.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A sharing listing already exists for this equipment");
        }

        SharingListing listing = new SharingListing();
        listing.setEquipment(equipment);
        listing.setOwnerInstitutionId(equipment.getInstitutionId());
        listing.setFeePerHour(request.getFeePerHour());
        listing.setExternalSharePercentage(request.getExternalSharePercentage());
        listing.setMinNoticeHours(request.getMinNoticeHours() != null ? request.getMinNoticeHours() : 0);
        listing.setMaxBookingDurationHours(request.getMaxBookingDurationHours());
        listing.setSharingTerms(request.getSharingTerms());
        listing.setActive(request.getActive() != null ? request.getActive() : true);
        listing.setCreatedByUserId(user.getId());

        SharingListing saved = listingRepository.save(listing);
        return toResponse(saved);
    }

    @Transactional
    public SharingListingResponse updateListing(Long listingId, SharingListingRequest request) {
        SharingListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + listingId));

        User user = getAuthenticatedUser();
        authorizeListingManagement(listing.getOwnerInstitutionId(), user);

        listing.setFeePerHour(request.getFeePerHour());
        listing.setExternalSharePercentage(request.getExternalSharePercentage());
        listing.setMinNoticeHours(request.getMinNoticeHours() != null ? request.getMinNoticeHours() : 0);
        listing.setMaxBookingDurationHours(request.getMaxBookingDurationHours());
        listing.setSharingTerms(request.getSharingTerms());
        if (request.getActive() != null) {
            listing.setActive(request.getActive());
        }

        SharingListing saved = listingRepository.save(listing);
        return toResponse(saved);
    }

    @Transactional
    public SharingListingResponse activateListing(Long listingId) {
        return setListingActive(listingId, true);
    }

    @Transactional
    public SharingListingResponse deactivateListing(Long listingId) {
        return setListingActive(listingId, false);
    }

    private SharingListingResponse setListingActive(Long listingId, boolean active) {
        SharingListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + listingId));

        User user = getAuthenticatedUser();
        authorizeListingManagement(listing.getOwnerInstitutionId(), user);

        listing.setActive(active);
        return toResponse(listingRepository.save(listing));
    }

    @Transactional(readOnly = true)
    public SharingListingResponse getListingById(Long listingId) {
        SharingListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + listingId));
        return toResponse(listing);
    }

    @Transactional(readOnly = true)
    public SharingListingResponse getListingByEquipmentId(Long equipmentId) {
        SharingListing listing = listingRepository.findByEquipmentId(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No listing found for equipment: " + equipmentId));
        return toResponse(listing);
    }

    /**
     * Requirement: Discovery API to display active listings from other institutions.
     * Avoids N+1 queries by fetching relationships in a batch.
     */
    @Transactional(readOnly = true)
    public Page<SharingListingDiscoveryResponse> discoverListings(
            Long filterInstitutionId,
            Long categoryId,
            String keyword,
            Pageable pageable) {

        User currentUser = findAuthenticatedUserOrNull();
        Long excludeInstitutionId = null;
        if (currentUser != null) {
            excludeInstitutionId = currentUser.getCollegeId() != null 
                    ? currentUser.getCollegeId() 
                    : currentUser.getInstitutionId();
        }

        String searchPattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;

        Page<SharingListing> listings = listingRepository.discoverListings(
                excludeInstitutionId, filterInstitutionId, categoryId, searchPattern, pageable);

        if (listings.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> equipmentIds = listings.getContent().stream()
                .map(l -> l.getEquipment().getId())
                .toList();

        // Batch fetch user's active agreements and latest requests (avoiding N+1)
        Map<Long, SharingAgreement> activeAgreementsMap = Collections.emptyMap();
        Map<Long, AccessRequest> latestRequestsMap = Collections.emptyMap();

        if (currentUser != null) {
            List<SharingAgreement> agreements = agreementRepository.findActiveAgreementsForUserAndEquipmentIn(
                    equipmentIds, currentUser.getId(), AgreementStatus.ACTIVE, GrantStatus.ACTIVE, LocalDate.now());
            activeAgreementsMap = agreements.stream()
                    .collect(Collectors.toMap(sa -> sa.getEquipment().getId(), sa -> sa, (a, b) -> a));

            List<AccessRequest> requests = requestRepository.findByRequesterIdAndEquipmentIdIn(
                    currentUser.getId(), equipmentIds);
            latestRequestsMap = requests.stream()
                    .collect(Collectors.toMap(ar -> ar.getEquipment().getId(), ar -> ar, (a, b) -> a));
        }

        final Map<Long, SharingAgreement> finalAgreementsMap = activeAgreementsMap;
        final Map<Long, AccessRequest> finalRequestsMap = latestRequestsMap;

        List<SharingListingDiscoveryResponse> responses = listings.getContent().stream()
                .map(l -> toDiscoveryResponse(l, finalAgreementsMap.get(l.getEquipment().getId()),
                        finalRequestsMap.get(l.getEquipment().getId())))
                .toList();

        return new PageImpl<>(responses, pageable, listings.getTotalElements());
    }

    private SharingListingDiscoveryResponse toDiscoveryResponse(
            SharingListing listing,
            SharingAgreement activeAgreement,
            AccessRequest latestRequest) {

        SharingListingDiscoveryResponse resp = new SharingListingDiscoveryResponse();
        resp.setListingId(listing.getId());
        resp.setEquipmentId(listing.getEquipment().getId());
        resp.setEquipmentName(listing.getEquipment().getName());
        resp.setAssetTag(listing.getEquipment().getAssetTag());
        resp.setDescription(listing.getEquipment().getDescription());
        resp.setSpecifications(listing.getEquipment().getSpecifications());
        resp.setLocation(listing.getEquipment().getLocation());

        if (listing.getEquipment().getCategory() != null) {
            resp.setCategoryId(listing.getEquipment().getCategory().getId());
            resp.setCategoryName(listing.getEquipment().getCategory().getName());
        } else if (listing.getEquipment().getLegacyCategory() != null) {
            resp.setCategoryName(listing.getEquipment().getLegacyCategory());
        }

        Long ownerInstId = listing.getEquipment().getCollegeId() != null
                ? listing.getEquipment().getCollegeId()
                : (listing.getEquipment().getInstitutionId() != null ? listing.getEquipment().getInstitutionId() : listing.getOwnerInstitutionId());
        resp.setOwnerInstitutionId(ownerInstId);
        resp.setOwnerInstitutionName(institutionService.getInstitutionName(ownerInstId));

        resp.setFeePerHour(listing.getFeePerHour());
        resp.setExternalSharePercentage(listing.getExternalSharePercentage());
        resp.setMinNoticeHours(listing.getMinNoticeHours());
        resp.setMaxBookingDurationHours(listing.getMaxBookingDurationHours());
        resp.setSharingTerms(listing.getSharingTerms());
        resp.setActive(listing.isActive());

        // Attach user-specific status
        if (latestRequest != null || activeAgreement != null) {
            SharingListingDiscoveryResponse.UserSharingStatus uStatus = new SharingListingDiscoveryResponse.UserSharingStatus();
            if (latestRequest != null) {
                uStatus.setLatestRequestId(latestRequest.getId());
                uStatus.setLatestRequestStatus(latestRequest.getStatus());
                uStatus.setRequestStartDate(latestRequest.getStartDate());
                uStatus.setRequestEndDate(latestRequest.getEndDate());
            }

            if (activeAgreement != null) {
                uStatus.setHasActiveAgreement(true);
                uStatus.setActiveAgreementId(activeAgreement.getId());
                uStatus.setAgreementStatus(activeAgreement.getStatus());
                uStatus.setAgreementStartDate(activeAgreement.getStartDate());
                uStatus.setAgreementEndDate(activeAgreement.getEndDate());
                uStatus.setAllowedStartTime(activeAgreement.getAllowedStartTime());
                uStatus.setAllowedEndTime(activeAgreement.getAllowedEndTime());
                uStatus.setAgreementFeePerHour(activeAgreement.getFeePerHour());
            } else {
                uStatus.setHasActiveAgreement(false);
            }
            resp.setUserStatus(uStatus);
        }

        return resp;
    }

    public SharingListingResponse toResponse(SharingListing l) {
        SharingListingResponse resp = new SharingListingResponse();
        resp.setId(l.getId());
        resp.setEquipmentId(l.getEquipment().getId());
        resp.setEquipmentName(l.getEquipment().getName());
        resp.setAssetTag(l.getEquipment().getAssetTag());
        resp.setOwnerInstitutionId(l.getOwnerInstitutionId());
        resp.setOwnerInstitutionName(institutionService.getInstitutionName(l.getOwnerInstitutionId()));
        resp.setFeePerHour(l.getFeePerHour());
        resp.setExternalSharePercentage(l.getExternalSharePercentage());
        resp.setMinNoticeHours(l.getMinNoticeHours());
        resp.setMaxBookingDurationHours(l.getMaxBookingDurationHours());
        resp.setSharingTerms(l.getSharingTerms());
        resp.setActive(l.isActive());
        resp.setCreatedAt(l.getCreatedAt());
        resp.setUpdatedAt(l.getUpdatedAt());
        return resp;
    }

    private void authorizeListingManagement(Long ownerInstitutionId, User user) {
        // Requirement: Only the Institution Administrator of the equipment's owner institution
        // and the System Administrator can create or manage listings.
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }

        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR) {
            if (user.getInstitutionId() != null && user.getInstitutionId().equals(ownerInstitutionId)) {
                return;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the owner institution administrator or system administrator can manage sharing listings"
        );
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

    private User findAuthenticatedUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}

