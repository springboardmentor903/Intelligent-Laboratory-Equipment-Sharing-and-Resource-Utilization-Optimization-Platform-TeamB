package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SharingAnalyticsService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final UtilizationRepository utilizationRepository;
    private final SharingAgreementRepository agreementRepository;
    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final InstitutionService institutionService;
    private final FeeCalculationService feeCalculationService;

    public SharingAnalyticsService(
            UtilizationRepository utilizationRepository,
            SharingAgreementRepository agreementRepository,
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            InstitutionService institutionService,
            FeeCalculationService feeCalculationService) {
        this.utilizationRepository = utilizationRepository;
        this.agreementRepository = agreementRepository;
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.institutionService = institutionService;
        this.feeCalculationService = feeCalculationService;
    }

    /**
     * Sharing Analytics Summary
     */
    @Transactional(readOnly = true)
    public SharingAnalyticsSummaryResponse getSummary(Long requestedInstitutionId) {
        User currentUser = getAuthenticatedUser();
        Long institutionId = resolveEffectiveInstitutionId(currentUser, requestedInstitutionId);

        String institutionName = institutionService.getInstitutionName(institutionId);

        // 1. Actual usage logs provided and used
        List<Utilization> providedList = getProvidedExternalUtilizations(institutionId);
        List<Utilization> usedList = getUsedExternalUtilizations(institutionId);

        long providedMinutes = providedList.stream()
                .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                .sum();
        long usedMinutes = usedList.stream()
                .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                .sum();

        BigDecimal totalHoursProvided = toHours(providedMinutes);
        BigDecimal totalHoursUsed = toHours(usedMinutes);

        // 2. Fees earned and owed
        Map<Long, SharingAgreement> agreementCache = new HashMap<>();
        BigDecimal feesEarned = providedList.stream()
                .map(u -> calculateUtilizationFee(u, agreementCache))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal feesOwed = usedList.stream()
                .map(u -> calculateUtilizationFee(u, agreementCache))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netBalance = feesEarned.subtract(feesOwed).setScale(2, RoundingMode.HALF_UP);

        // 3. Expected fees from active / upcoming external bookings
        BigDecimal expectedFees = calculateExpectedFees(institutionId, agreementCache);

        // 4. Bookings under agreements and no-show metrics
        List<Booking> sharingBookings = bookingRepository.findAllSharingBookingsForInstitution(institutionId);
        long totalBookings = sharingBookings.size();
        long noShowCount = sharingBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.NO_SHOW)
                .count();
        BigDecimal noShowRate = totalBookings > 0
                ? BigDecimal.valueOf(noShowCount * 100.0 / totalBookings).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 5. Agreements & Partners
        LocalDate today = LocalDate.now();
        List<SharingAgreement> allAgreements = agreementRepository.findAllByInstitutionInvolved(institutionId);

        long activeAgreements = allAgreements.stream()
                .filter(a -> a.getStatus() == AgreementStatus.ACTIVE && (a.getEndDate() == null || !a.getEndDate().isBefore(today)))
                .count();

        long expiringAgreements = allAgreements.stream()
                .filter(a -> a.getStatus() == AgreementStatus.ACTIVE
                        && a.getEndDate() != null
                        && !a.getEndDate().isBefore(today)
                        && !a.getEndDate().isAfter(today.plusDays(30)))
                .count();

        Set<Long> partnerIds = new HashSet<>();
        for (SharingAgreement a : allAgreements) {
            Long other = getPartnerInstitutionIdFromAgreement(a, institutionId);
            if (other != null && !other.equals(institutionId)) {
                partnerIds.add(other);
            }
        }
        long totalPartners = partnerIds.size();

        // 6. External share percentage of equipment usage
        List<Utilization> allEquipUtilizations = utilizationRepository.findAllByEquipmentInstitution(institutionId);
        long totalEquipmentMinutes = allEquipUtilizations.stream()
                .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                .sum();
        BigDecimal externalSharePercentage = totalEquipmentMinutes > 0
                ? BigDecimal.valueOf(providedMinutes * 100.0 / totalEquipmentMinutes).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return SharingAnalyticsSummaryResponse.builder()
                .institutionId(institutionId)
                .institutionName(institutionName)
                .totalHoursProvided(totalHoursProvided)
                .totalHoursUsed(totalHoursUsed)
                .feesEarned(feesEarned)
                .feesOwed(feesOwed)
                .netBalance(netBalance)
                .expectedFees(expectedFees)
                .totalBookings(totalBookings)
                .noShowCount(noShowCount)
                .noShowRate(noShowRate)
                .activeAgreements(activeAgreements)
                .totalPartners(totalPartners)
                .expiringAgreementsCount(expiringAgreements)
                .externalSharePercentage(externalSharePercentage)
                .build();
    }

    /**
     * Sharing Trends (Weekly and Monthly)
     */
    @Transactional(readOnly = true)
    public SharingTrendResponse getTrends(Long requestedInstitutionId, String periodParam, LocalDate startDate, LocalDate endDate) {
        User currentUser = getAuthenticatedUser();
        Long institutionId = resolveEffectiveInstitutionId(currentUser, requestedInstitutionId);
        String institutionName = institutionService.getInstitutionName(institutionId);

        boolean isWeekly = "WEEKLY".equalsIgnoreCase(periodParam);
        String period = isWeekly ? "WEEKLY" : "MONTHLY";

        List<Utilization> providedList = getProvidedExternalUtilizations(institutionId);
        List<Utilization> usedList = getUsedExternalUtilizations(institutionId);
        Map<Long, SharingAgreement> agreementCache = new HashMap<>();

        List<SharingTrendItem> items;
        if (isWeekly) {
            items = buildWeeklyTrends(providedList, usedList, startDate, endDate, agreementCache);
        } else {
            items = buildMonthlyTrends(providedList, usedList, startDate, endDate, agreementCache);
        }

        return SharingTrendResponse.builder()
                .institutionId(institutionId)
                .institutionName(institutionName)
                .period(period)
                .items(items)
                .build();
    }

    /**
     * Partner Analytics
     */
    @Transactional(readOnly = true)
    public Page<PartnerAnalyticsItem> getPartnerAnalytics(
            Long requestedInstitutionId, Pageable pageable, String sortBy, String direction) {
        User currentUser = getAuthenticatedUser();
        Long institutionId = resolveEffectiveInstitutionId(currentUser, requestedInstitutionId);

        List<SharingAgreement> allAgreements = agreementRepository.findAllByInstitutionInvolved(institutionId);
        List<Utilization> providedList = getProvidedExternalUtilizations(institutionId);
        List<Utilization> usedList = getUsedExternalUtilizations(institutionId);
        List<Booking> allSharingBookings = bookingRepository.findAllSharingBookingsForInstitution(institutionId);

        // Gather all distinct partner institution IDs
        Set<Long> partnerIds = new HashSet<>();
        for (SharingAgreement a : allAgreements) {
            Long pId = getPartnerInstitutionIdFromAgreement(a, institutionId);
            if (pId != null && !pId.equals(institutionId)) {
                partnerIds.add(pId);
            }
        }
        for (Utilization u : providedList) {
            Long pId = getUserInstitutionId(u.getUser());
            if (pId != null && !pId.equals(institutionId)) {
                partnerIds.add(pId);
            }
        }
        for (Utilization u : usedList) {
            Long pId = getEquipmentInstitutionId(u.getEquipment());
            if (pId != null && !pId.equals(institutionId)) {
                partnerIds.add(pId);
            }
        }
        for (Booking b : allSharingBookings) {
            Long otherId = getOtherInstitutionFromBooking(b, institutionId);
            if (otherId != null && !otherId.equals(institutionId)) {
                partnerIds.add(otherId);
            }
        }

        Map<Long, SharingAgreement> agreementCache = new HashMap<>();
        LocalDate today = LocalDate.now();

        List<PartnerAnalyticsItem> items = new ArrayList<>();
        for (Long partnerId : partnerIds) {
            String partnerName = institutionService.getInstitutionName(partnerId);

            long provMinutes = providedList.stream()
                    .filter(u -> Objects.equals(getUserInstitutionId(u.getUser()), partnerId))
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            long usdMinutes = usedList.stream()
                    .filter(u -> Objects.equals(getEquipmentInstitutionId(u.getEquipment()), partnerId))
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            BigDecimal feesEarned = providedList.stream()
                    .filter(u -> Objects.equals(getUserInstitutionId(u.getUser()), partnerId))
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal feesOwed = usedList.stream()
                    .filter(u -> Objects.equals(getEquipmentInstitutionId(u.getEquipment()), partnerId))
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal balance = feesEarned.subtract(feesOwed).setScale(2, RoundingMode.HALF_UP);

            long activeAgreementsCount = allAgreements.stream()
                    .filter(a -> a.getStatus() == AgreementStatus.ACTIVE
                            && (a.getEndDate() == null || !a.getEndDate().isBefore(today))
                            && Objects.equals(getPartnerInstitutionIdFromAgreement(a, institutionId), partnerId))
                    .count();

            long totalBookings = allSharingBookings.stream()
                    .filter(b -> Objects.equals(getOtherInstitutionFromBooking(b, institutionId), partnerId))
                    .count();

            items.add(PartnerAnalyticsItem.builder()
                    .partnerInstitutionId(partnerId)
                    .partnerInstitutionName(partnerName)
                    .hoursProvided(toHours(provMinutes))
                    .hoursUsed(toHours(usdMinutes))
                    .feesEarned(feesEarned)
                    .feesOwed(feesOwed)
                    .balance(balance)
                    .activeAgreementsCount(activeAgreementsCount)
                    .totalBookings(totalBookings)
                    .build());
        }

        // Sorting
        Comparator<PartnerAnalyticsItem> comparator = getPartnerAnalyticsComparator(sortBy, direction);
        items.sort(comparator);

        // Pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), items.size());
        List<PartnerAnalyticsItem> pageContent = (start < items.size())
                ? items.subList(start, end)
                : Collections.emptyList();

        return new PageImpl<>(pageContent, pageable, items.size());
    }

    /**
     * Partnership Report
     */
    @Transactional(readOnly = true)
    public PartnershipReportResponse getPartnershipReport(Long requestedInstitutionId, Long partnerInstitutionId) {
        if (partnerInstitutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerInstitutionId is required");
        }

        User currentUser = getAuthenticatedUser();
        Long institutionId = resolveEffectiveInstitutionId(currentUser, requestedInstitutionId);

        if (institutionId.equals(partnerInstitutionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution cannot generate a partnership report with itself");
        }

        String institutionName = institutionService.getInstitutionName(institutionId);
        String partnerName = institutionService.getInstitutionName(partnerInstitutionId);

        List<Utilization> providedList = getProvidedExternalUtilizations(institutionId).stream()
                .filter(u -> Objects.equals(getUserInstitutionId(u.getUser()), partnerInstitutionId))
                .toList();

        List<Utilization> usedList = getUsedExternalUtilizations(institutionId).stream()
                .filter(u -> Objects.equals(getEquipmentInstitutionId(u.getEquipment()), partnerInstitutionId))
                .toList();

        Map<Long, SharingAgreement> agreementCache = new HashMap<>();

        long provMinutes = providedList.stream()
                .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                .sum();
        long usdMinutes = usedList.stream()
                .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                .sum();

        BigDecimal hoursProvided = toHours(provMinutes);
        BigDecimal hoursUsed = toHours(usdMinutes);
        BigDecimal totalHours = hoursProvided.add(hoursUsed).setScale(2, RoundingMode.HALF_UP);

        BigDecimal feesEarned = providedList.stream()
                .map(u -> calculateUtilizationFee(u, agreementCache))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal feesOwed = usedList.stream()
                .map(u -> calculateUtilizationFee(u, agreementCache))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal balance = feesEarned.subtract(feesOwed).setScale(2, RoundingMode.HALF_UP);

        List<Booking> bookings = bookingRepository.findAllSharingBookingsForInstitution(institutionId).stream()
                .filter(b -> Objects.equals(getOtherInstitutionFromBooking(b, institutionId), partnerInstitutionId))
                .toList();

        long totalBookings = bookings.size();
        long noShowCount = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.NO_SHOW)
                .count();
        BigDecimal noShowRate = totalBookings > 0
                ? BigDecimal.valueOf(noShowCount * 100.0 / totalBookings).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Monthly trends between these two institutions
        List<SharingTrendItem> monthlyTrends = buildMonthlyTrends(
                providedList, usedList, null, null, agreementCache);

        // Agreement details
        List<SharingAgreement> agreementsBetween = agreementRepository
                .findAgreementsBetweenInstitutions(institutionId, partnerInstitutionId);

        LocalDate today = LocalDate.now();
        List<PartnershipReportResponse.PartnershipAgreementDetail> agreementDetails = agreementsBetween.stream()
                .map(a -> {
                    String status = "ACTIVE";
                    if (a.getStatus() == AgreementStatus.REVOKED) {
                        status = "REVOKED";
                    } else if (a.getEndDate() != null && a.getEndDate().isBefore(today)) {
                        status = "EXPIRED";
                    }

                    return PartnershipReportResponse.PartnershipAgreementDetail.builder()
                            .agreementId(a.getId())
                            .equipmentId(a.getEquipment() != null ? a.getEquipment().getId() : null)
                            .equipmentName(a.getEquipment() != null ? a.getEquipment().getName() : null)
                            .assetTag(a.getEquipment() != null ? a.getEquipment().getAssetTag() : null)
                            .ownerInstitutionId(a.getOwnerInstitutionId())
                            .partnerInstitutionId(a.getPartnerInstitutionId())
                            .startDate(a.getStartDate())
                            .endDate(a.getEndDate())
                            .feePerHour(a.getFeePerHour())
                            .status(status)
                            .allowedStartTime(a.getAllowedStartTime())
                            .allowedEndTime(a.getAllowedEndTime())
                            .build();
                })
                .toList();

        return PartnershipReportResponse.builder()
                .institutionId(institutionId)
                .institutionName(institutionName)
                .partnerInstitutionId(partnerInstitutionId)
                .partnerInstitutionName(partnerName)
                .hoursProvided(hoursProvided)
                .hoursUsed(hoursUsed)
                .totalHours(totalHours)
                .feesEarned(feesEarned)
                .feesOwed(feesOwed)
                .balance(balance)
                .totalBookings(totalBookings)
                .noShowCount(noShowCount)
                .noShowRate(noShowRate)
                .monthlyTrends(monthlyTrends)
                .agreements(agreementDetails)
                .build();
    }

    /**
     * Most Shared Equipment
     */
    @Transactional(readOnly = true)
    public Page<MostSharedEquipmentItem> getMostSharedEquipment(
            Long requestedInstitutionId, Pageable pageable, String sortBy, String direction) {
        User currentUser = getAuthenticatedUser();
        Long institutionId = resolveEffectiveInstitutionId(currentUser, requestedInstitutionId);

        List<Equipment> equipmentList = equipmentRepository
                .findByInstitutionIdOrCollegeId(institutionId, institutionId);

        List<Utilization> providedList = getProvidedExternalUtilizations(institutionId);
        List<Utilization> allEquipUtilizations = utilizationRepository
                .findAllByEquipmentInstitution(institutionId);

        Map<Long, List<Utilization>> externalByEquip = providedList.stream()
                .filter(u -> u.getEquipment() != null)
                .collect(Collectors.groupingBy(u -> u.getEquipment().getId()));

        Map<Long, List<Utilization>> allByEquip = allEquipUtilizations.stream()
                .filter(u -> u.getEquipment() != null)
                .collect(Collectors.groupingBy(u -> u.getEquipment().getId()));

        Map<Long, SharingAgreement> agreementCache = new HashMap<>();

        List<MostSharedEquipmentItem> items = new ArrayList<>();
        for (Equipment eq : equipmentList) {
            List<Utilization> extUtils = externalByEquip.getOrDefault(eq.getId(), Collections.emptyList());
            List<Utilization> allUtils = allByEquip.getOrDefault(eq.getId(), Collections.emptyList());

            long provMinutes = extUtils.stream()
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();
            long totalMinutes = allUtils.stream()
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            BigDecimal feesEarned = extUtils.stream()
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal extSharePct = totalMinutes > 0
                    ? BigDecimal.valueOf(provMinutes * 100.0 / totalMinutes).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            long bookingCount = bookingRepository.findExternalBookingsByEquipmentId(eq.getId()).size();

            String categoryName = eq.getCategory() != null ? eq.getCategory().getName() : null;

            items.add(MostSharedEquipmentItem.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag())
                    .categoryName(categoryName)
                    .providedMinutes(provMinutes)
                    .providedHours(toHours(provMinutes))
                    .totalUsageMinutes(totalMinutes)
                    .totalUsageHours(toHours(totalMinutes))
                    .feesEarned(feesEarned)
                    .externalSharePercentage(extSharePct)
                    .bookingCount(bookingCount)
                    .build());
        }

        Comparator<MostSharedEquipmentItem> comparator = getMostSharedEquipmentComparator(sortBy, direction);
        items.sort(comparator);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), items.size());
        List<MostSharedEquipmentItem> pageContent = (start < items.size())
                ? items.subList(start, end)
                : Collections.emptyList();

        return new PageImpl<>(pageContent, pageable, items.size());
    }

    // ==========================================
    // Internal Helper Methods
    // ==========================================

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    public Long resolveEffectiveInstitutionId(User currentUser, Long requestedInstitutionId) {
        if (currentUser.getRole() != User.Role.INSTITUTION_ADMINISTRATOR
                && currentUser.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Institution Administrators and System Administrators can access sharing analytics");
        }

        if (currentUser.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            if (requestedInstitutionId != null) {
                return requestedInstitutionId;
            }
            Long fallback = currentUser.getInstitutionId() != null
                    ? currentUser.getInstitutionId()
                    : currentUser.getCollegeId();
            return fallback != null ? fallback : 1L;
        }

        // INSTITUTION_ADMINISTRATOR
        Long userInstId = currentUser.getInstitutionId() != null
                ? currentUser.getInstitutionId()
                : currentUser.getCollegeId();
        if (userInstId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution Administrator is not assigned to an institution");
        }
        if (requestedInstitutionId != null && !requestedInstitutionId.equals(userInstId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Institution Administrators can only access analytics for their own institution");
        }
        return userInstId;
    }

    private List<Utilization> getProvidedExternalUtilizations(Long institutionId) {
        return utilizationRepository.findAllProvidedUtilizations(institutionId).stream()
                .filter(u -> isProvidedExternal(u, institutionId))
                .toList();
    }

    private List<Utilization> getUsedExternalUtilizations(Long institutionId) {
        return utilizationRepository.findAllUsedExternalUtilizations(institutionId);
    }

    private boolean isProvidedExternal(Utilization u, Long institutionId) {
        if (u.getUsedMinutes() == null) {
            return false;
        }
        if (u.getUsageType() == UsageType.SHARED) {
            return true;
        }
        if (u.getBooking() != null && u.getBooking().isExternal()) {
            return true;
        }
        Long userInst = getUserInstitutionId(u.getUser());
        return userInst != null && !userInst.equals(institutionId);
    }

    private Long getUserInstitutionId(User user) {
        if (user == null) return null;
        return user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
    }

    private Long getEquipmentInstitutionId(Equipment eq) {
        if (eq == null) return null;
        return eq.getInstitutionId() != null ? eq.getInstitutionId() : eq.getCollegeId();
    }

    private Long getPartnerInstitutionIdFromAgreement(SharingAgreement a, Long targetInstitutionId) {
        if (Objects.equals(a.getOwnerInstitutionId(), targetInstitutionId)) {
            return a.getPartnerInstitutionId();
        }
        if (Objects.equals(a.getPartnerInstitutionId(), targetInstitutionId)) {
            return a.getOwnerInstitutionId();
        }
        return a.getPartnerInstitutionId();
    }

    private Long getOtherInstitutionFromBooking(Booking b, Long targetInstitutionId) {
        Long eqInst = getEquipmentInstitutionId(b.getEquipment());
        Long userInst = getUserInstitutionId(b.getUser());
        if (Objects.equals(eqInst, targetInstitutionId)) {
            return userInst;
        }
        if (Objects.equals(userInst, targetInstitutionId)) {
            return eqInst;
        }
        return null;
    }

    public BigDecimal calculateUtilizationFee(Utilization u, Map<Long, SharingAgreement> agreementCache) {
        if (u.getActualFee() != null) {
            return u.getActualFee().setScale(2, RoundingMode.HALF_UP);
        }
        if (u.getBooking() != null && u.getBooking().getActualFee() != null) {
            return u.getBooking().getActualFee().setScale(2, RoundingMode.HALF_UP);
        }
        long minutes = u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L;
        if (minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (u.getBooking() != null && u.getBooking().getFeePerHour() != null) {
            return feeCalculationService.calculateFee(minutes, u.getBooking().getFeePerHour());
        }

        if (u.getBooking() != null && u.getBooking().getSharingAgreementId() != null) {
            Long agrId = u.getBooking().getSharingAgreementId();
            SharingAgreement sa = agreementCache.computeIfAbsent(agrId,
                    id -> agreementRepository.findById(id).orElse(null));
            if (sa != null && sa.getFeePerHour() != null) {
                return feeCalculationService.calculateFee(minutes, sa.getFeePerHour());
            }
        }

        if (u.getEquipment() != null && u.getEquipment().getHourlyCost() != null) {
            return feeCalculationService.calculateFee(minutes, u.getEquipment().getHourlyCost());
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateExpectedFees(Long institutionId, Map<Long, SharingAgreement> agreementCache) {
        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_USE
        );
        List<Booking> upcoming = bookingRepository.findExternalBookingsForEquipmentOwnerAndStatuses(
                institutionId, activeStatuses);

        BigDecimal total = BigDecimal.ZERO;
        for (Booking b : upcoming) {
            if (b.getEstimatedFee() != null) {
                total = total.add(b.getEstimatedFee());
            } else {
                long durationMinutes = Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes());
                BigDecimal rate = b.getFeePerHour();
                if (rate == null && b.getSharingAgreementId() != null) {
                    SharingAgreement sa = agreementCache.computeIfAbsent(
                            b.getSharingAgreementId(), id -> agreementRepository.findById(id).orElse(null));
                    if (sa != null) {
                        rate = sa.getFeePerHour();
                    }
                }
                if (rate == null && b.getEquipment() != null) {
                    rate = b.getEquipment().getHourlyCost();
                }
                total = total.add(feeCalculationService.calculateFee(durationMinutes, rate));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toHours(long minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private List<SharingTrendItem> buildWeeklyTrends(
            List<Utilization> providedList,
            List<Utilization> usedList,
            LocalDate startDate,
            LocalDate endDate,
            Map<Long, SharingAgreement> agreementCache) {

        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusWeeks(11);
        }

        LocalDate normStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate normEnd = endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<SharingTrendItem> trends = new ArrayList<>();
        LocalDate curr = normStart;

        while (!curr.isAfter(normEnd)) {
            LocalDate wStart = curr;
            LocalDate wEnd = curr.plusDays(6);

            int weekNum = wStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int year = wStart.get(IsoFields.WEEK_BASED_YEAR);
            String label = String.format("%d-W%02d", year, weekNum);

            Instant startInst = wStart.atStartOfDay(IST_ZONE).toInstant();
            Instant endInst = wEnd.atTime(LocalTime.MAX).atZone(IST_ZONE).toInstant();

            long provMins = providedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            long usdMins = usedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            BigDecimal feesEarned = providedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal feesOwed = usedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal netBalance = feesEarned.subtract(feesOwed).setScale(2, RoundingMode.HALF_UP);

            trends.add(SharingTrendItem.builder()
                    .periodLabel(label)
                    .startDate(wStart)
                    .endDate(wEnd)
                    .hoursProvided(toHours(provMins))
                    .hoursUsed(toHours(usdMins))
                    .feesEarned(feesEarned)
                    .feesOwed(feesOwed)
                    .netBalance(netBalance)
                    .build());

            curr = curr.plusWeeks(1);
        }

        return trends;
    }

    private List<SharingTrendItem> buildMonthlyTrends(
            List<Utilization> providedList,
            List<Utilization> usedList,
            LocalDate startDate,
            LocalDate endDate,
            Map<Long, SharingAgreement> agreementCache) {

        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusMonths(5).withDayOfMonth(1);
        }

        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(endDate);

        List<SharingTrendItem> trends = new ArrayList<>();
        YearMonth curr = startYm;

        while (!curr.isAfter(endYm)) {
            LocalDate mStart = curr.atDay(1);
            LocalDate mEnd = curr.atEndOfMonth();
            String label = curr.toString(); // "2026-11"

            Instant startInst = mStart.atStartOfDay(IST_ZONE).toInstant();
            Instant endInst = mEnd.atTime(LocalTime.MAX).atZone(IST_ZONE).toInstant();

            long provMins = providedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            long usdMins = usedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .mapToLong(u -> u.getUsedMinutes() != null ? u.getUsedMinutes() : 0L)
                    .sum();

            BigDecimal feesEarned = providedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal feesOwed = usedList.stream()
                    .filter(u -> isWithin(u.getCheckInTime(), startInst, endInst))
                    .map(u -> calculateUtilizationFee(u, agreementCache))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal netBalance = feesEarned.subtract(feesOwed).setScale(2, RoundingMode.HALF_UP);

            trends.add(SharingTrendItem.builder()
                    .periodLabel(label)
                    .startDate(mStart)
                    .endDate(mEnd)
                    .hoursProvided(toHours(provMins))
                    .hoursUsed(toHours(usdMins))
                    .feesEarned(feesEarned)
                    .feesOwed(feesOwed)
                    .netBalance(netBalance)
                    .build());

            curr = curr.plusMonths(1);
        }

        return trends;
    }

    private boolean isWithin(Instant time, Instant start, Instant end) {
        if (time == null) return false;
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private Comparator<PartnerAnalyticsItem> getPartnerAnalyticsComparator(String sortBy, String direction) {
        Comparator<PartnerAnalyticsItem> comp;
        String field = sortBy != null ? sortBy.toLowerCase() : "balance";

        switch (field) {
            case "hoursprovided" -> comp = Comparator.comparing(PartnerAnalyticsItem::getHoursProvided);
            case "hoursused" -> comp = Comparator.comparing(PartnerAnalyticsItem::getHoursUsed);
            case "feesearned" -> comp = Comparator.comparing(PartnerAnalyticsItem::getFeesEarned);
            case "feesowed" -> comp = Comparator.comparing(PartnerAnalyticsItem::getFeesOwed);
            case "partnerinstitutionname", "name" -> comp = Comparator.comparing(
                    p -> p.getPartnerInstitutionName() != null ? p.getPartnerInstitutionName().toLowerCase() : "");
            case "totalbookings" -> comp = Comparator.comparing(PartnerAnalyticsItem::getTotalBookings);
            case "activeagreementscount" -> comp = Comparator.comparing(PartnerAnalyticsItem::getActiveAgreementsCount);
            default -> comp = Comparator.comparing(PartnerAnalyticsItem::getBalance);
        }

        if ("ASC".equalsIgnoreCase(direction)) {
            return comp;
        }
        return comp.reversed();
    }

    private Comparator<MostSharedEquipmentItem> getMostSharedEquipmentComparator(String sortBy, String direction) {
        Comparator<MostSharedEquipmentItem> comp;
        String field = sortBy != null ? sortBy.toLowerCase() : "providedminutes";

        switch (field) {
            case "providedminutes", "providedhours" -> comp = Comparator.comparing(MostSharedEquipmentItem::getProvidedMinutes);
            case "feesearned" -> comp = Comparator.comparing(MostSharedEquipmentItem::getFeesEarned);
            case "externalsharepercentage" -> comp = Comparator.comparing(MostSharedEquipmentItem::getExternalSharePercentage);
            case "bookingcount" -> comp = Comparator.comparing(MostSharedEquipmentItem::getBookingCount);
            case "totalusageminutes", "totalusagehours" -> comp = Comparator.comparing(MostSharedEquipmentItem::getTotalUsageMinutes);
            case "equipmentname", "name" -> comp = Comparator.comparing(
                    e -> e.getEquipmentName() != null ? e.getEquipmentName().toLowerCase() : "");
            default -> comp = Comparator.comparing(MostSharedEquipmentItem::getProvidedMinutes);
        }

        if ("ASC".equalsIgnoreCase(direction)) {
            return comp;
        }
        return comp.reversed();
    }
}

