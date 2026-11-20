package com.campus.lab_resource_platform.billing.service;

import com.campus.lab_resource_platform.billing.dto.UsageChargeListResponse;
import com.campus.lab_resource_platform.billing.dto.UsageChargeResponse;
import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import com.campus.lab_resource_platform.billing.repository.UsageChargeRepository;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class UsageChargeService {

    private static final Logger log = LoggerFactory.getLogger(UsageChargeService.class);

    private final UsageChargeRepository chargeRepository;
    private final FeeCalculationService feeCalculationService;
    private final SharingAgreementRepository agreementRepository;
    private final UserRepository userRepository;

    public UsageChargeService(
            UsageChargeRepository chargeRepository,
            FeeCalculationService feeCalculationService,
            SharingAgreementRepository agreementRepository,
            UserRepository userRepository) {
        this.chargeRepository = chargeRepository;
        this.feeCalculationService = feeCalculationService;
        this.agreementRepository = agreementRepository;
        this.userRepository = userRepository;
    }

    /**
     * Automatically create one usage charge for every completed booking within the same transaction.
     * Do not create charges for cancelled bookings, No Shows, or zero usage.
     */
    @Transactional
    public Optional<UsageCharge> recordUsageCharge(Booking booking, Long usedMinutes) {
        if (booking == null || booking.getStatus() != BookingStatus.COMPLETED) {
            return Optional.empty();
        }
        if (usedMinutes == null || usedMinutes <= 0) {
            log.info("Skipping usage charge for booking #{}: zero or negative usage minutes", booking.getId());
            return Optional.empty();
        }
        if (chargeRepository.existsByBookingId(booking.getId())) {
            log.info("Usage charge already exists for booking #{}. Skipping duplicate.", booking.getId());
            return chargeRepository.findByBookingId(booking.getId());
        }

        Equipment equipment = booking.getEquipment();
        User user = booking.getUser();
        if (equipment == null || user == null) {
            return Optional.empty();
        }

        Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
        if (eqInstId == null) {
            eqInstId = 1L;
        }

        Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();

        ChargeType chargeType;
        BigDecimal hourlyRate;

        if (userInstId == null) {
            // UNASSIGNED – Users without an institution; record the cost but do not invoice it.
            chargeType = ChargeType.UNASSIGNED;
            hourlyRate = equipment.getHourlyCost() != null ? equipment.getHourlyCost() : BigDecimal.valueOf(200.00);
        } else if (Objects.equals(userInstId, eqInstId)) {
            // INTERNAL – Users belonging to the equipment-owning institution.
            chargeType = ChargeType.INTERNAL;
            hourlyRate = equipment.getHourlyCost() != null ? equipment.getHourlyCost() : BigDecimal.valueOf(250.00);
        } else {
            // EXTERNAL – Users belonging to another institution, based on sharing agreement fee.
            chargeType = ChargeType.EXTERNAL;
            if (booking.getFeePerHour() != null) {
                hourlyRate = booking.getFeePerHour();
            } else if (booking.getSharingAgreementId() != null) {
                Optional<SharingAgreement> agr = agreementRepository.findById(booking.getSharingAgreementId());
                hourlyRate = agr.map(SharingAgreement::getFeePerHour)
                        .orElse(equipment.getHourlyCost() != null ? equipment.getHourlyCost() : BigDecimal.valueOf(350.00));
            } else {
                hourlyRate = equipment.getHourlyCost() != null ? equipment.getHourlyCost() : BigDecimal.valueOf(350.00);
            }
        }

        BigDecimal totalCharge = feeCalculationService.calculateFee(usedMinutes, hourlyRate);

        Long deptId = user.getDepartmentId() != null ? user.getDepartmentId() : equipment.getDepartmentId();

        UsageCharge charge = UsageCharge.builder()
                .booking(booking)
                .equipment(equipment)
                .user(user)
                .institutionId(eqInstId)
                .departmentId(deptId)
                .chargeType(chargeType)
                .hourlyRateSnapshot(hourlyRate)
                .usedMinutes(usedMinutes.intValue())
                .totalCharge(totalCharge)
                .createdAt(Instant.now())
                .build();

        UsageCharge saved = chargeRepository.save(charge);
        log.info("Recorded {} usage charge #{} for booking #{}: {} mins at ₹{}/hr = ₹{}",
                chargeType, saved.getId(), booking.getId(), usedMinutes, hourlyRate, totalCharge);
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public Page<UsageChargeResponse> getMyCharges(String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        return chargeRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(UsageChargeResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UsageChargeListResponse getInstitutionCharges(
            ChargeType chargeType,
            Long departmentId,
            Long equipmentId,
            Instant startDate,
            Instant endDate,
            String userEmail,
            Pageable pageable) {
        User user = getUserByEmail(userEmail);

        if (user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR && user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only Institution Administrators and Lab Managers can view institutional usage charges");
        }

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        Page<UsageCharge> page = chargeRepository.findByInstitutionWithFilters(
                instId, chargeType, departmentId, equipmentId, startDate, endDate, pageable);

        List<UsageCharge> allInPeriod = chargeRepository.findAllByInstitutionInPeriod(instId, startDate, endDate);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalInternal = BigDecimal.ZERO;
        BigDecimal totalExternal = BigDecimal.ZERO;
        BigDecimal totalUnassigned = BigDecimal.ZERO;

        for (UsageCharge c : allInPeriod) {
            if (c.getTotalCharge() == null) continue;
            total = total.add(c.getTotalCharge());
            if (c.getChargeType() == ChargeType.INTERNAL) {
                totalInternal = totalInternal.add(c.getTotalCharge());
            } else if (c.getChargeType() == ChargeType.EXTERNAL) {
                totalExternal = totalExternal.add(c.getTotalCharge());
            } else if (c.getChargeType() == ChargeType.UNASSIGNED) {
                totalUnassigned = totalUnassigned.add(c.getTotalCharge());
            }
        }

        return UsageChargeListResponse.builder()
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .totalAmount(total)
                .totalInternalAmount(totalInternal)
                .totalExternalAmount(totalExternal)
                .totalUnassignedAmount(totalUnassigned)
                .charges(page.getContent().stream().map(UsageChargeResponse::fromEntity).toList())
                .build();
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
