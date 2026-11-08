package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.sharing.dto.AgreementUsageFeesResponse;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.repository.AccessGrantRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeeCalculationService {

    private final SharingAgreementRepository agreementRepository;
    private final AccessGrantRepository grantRepository;
    private final BookingRepository bookingRepository;
    private final UtilizationRepository utilizationRepository;
    private final UserRepository userRepository;

    public FeeCalculationService(
            SharingAgreementRepository agreementRepository,
            AccessGrantRepository grantRepository,
            BookingRepository bookingRepository,
            UtilizationRepository utilizationRepository,
            UserRepository userRepository) {
        this.agreementRepository = agreementRepository;
        this.grantRepository = grantRepository;
        this.bookingRepository = bookingRepository;
        this.utilizationRepository = utilizationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Fee = (Used or Booked Minutes × Fee per Hour) / 60
     * Rounded only at the end to 2 decimal places using HALF_UP.
     */
    public BigDecimal calculateFee(long minutes, BigDecimal feePerHour) {
        if (feePerHour == null || minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal minutesBd = BigDecimal.valueOf(minutes);
        BigDecimal sixty = BigDecimal.valueOf(60);
        return minutesBd.multiply(feePerHour)
                .divide(sixty, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateEstimatedFee(LocalDateTime start, LocalDateTime end, BigDecimal feePerHour) {
        if (start == null || end == null || feePerHour == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long minutes = Math.max(0, Duration.between(start, end).toMinutes());
        return calculateFee(minutes, feePerHour);
    }

    public BigDecimal calculateActualFee(long usedMinutes, BigDecimal feePerHour) {
        return calculateFee(usedMinutes, feePerHour);
    }

    @Transactional(readOnly = true)
    public AgreementUsageFeesResponse getAgreementUsageFees(Long agreementId, LocalDate startDate, LocalDate endDate) {
        SharingAgreement agreement = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agreement not found: " + agreementId));

        User currentUser = getAuthenticatedUser();
        authorizeUsageFeesAccess(agreement, currentUser);

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        List<Booking> bookings = bookingRepository.findAgreementBookingsInPeriod(
                agreementId, startDateTime, endDateTime);

        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, Utilization> utilizationMap = utilizationRepository.findByBookingIdIn(bookingIds).stream()
                .filter(u -> u.getBooking() != null)
                .collect(Collectors.toMap(u -> u.getBooking().getId(), u -> u, (a, b) -> a));

        AgreementUsageFeesResponse response = new AgreementUsageFeesResponse();
        response.setAgreementId(agreement.getId());
        response.setEquipmentId(agreement.getEquipment().getId());
        response.setEquipmentName(agreement.getEquipment().getName());
        response.setFeePerHour(agreement.getFeePerHour());

        long totalBooked = 0L;
        long totalUsed = 0L;
        BigDecimal totalEstimated = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        for (Booking b : bookings) {
            long bookedMins = Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes());
            BigDecimal estFee = b.getEstimatedFee() != null
                    ? b.getEstimatedFee()
                    : calculateFee(bookedMins, agreement.getFeePerHour());

            Utilization util = utilizationMap.get(b.getId());
            Long usedMins = null;
            BigDecimal actualFee = null;

            if (util != null && util.getUsedMinutes() != null) {
                usedMins = util.getUsedMinutes().longValue();
                actualFee = util.getActualFee() != null
                        ? util.getActualFee()
                        : calculateFee(usedMins, agreement.getFeePerHour());
            } else if (b.getActualFee() != null) {
                actualFee = b.getActualFee();
            }

            totalBooked += bookedMins;
            totalEstimated = totalEstimated.add(estFee);

            if (usedMins != null) {
                totalUsed += usedMins;
            }
            if (actualFee != null) {
                totalActual = totalActual.add(actualFee);
            }

            AgreementUsageFeesResponse.BookingFeeItem item = new AgreementUsageFeesResponse.BookingFeeItem();
            item.setBookingId(b.getId());
            item.setStartTime(b.getStartTime());
            item.setEndTime(b.getEndTime());
            item.setBookedMinutes(bookedMins);
            item.setUsedMinutes(usedMins);
            item.setFeePerHour(agreement.getFeePerHour());
            item.setEstimatedFee(estFee);
            item.setActualFee(actualFee);
            item.setStatus(b.getStatus());

            response.getItems().add(item);
        }

        response.setTotalBookedMinutes(totalBooked);
        response.setTotalUsedMinutes(totalUsed);
        response.setTotalEstimatedFee(totalEstimated.setScale(2, RoundingMode.HALF_UP));
        response.setTotalActualFee(totalActual.setScale(2, RoundingMode.HALF_UP));

        return response;
    }

    private void authorizeUsageFeesAccess(SharingAgreement agreement, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }

        // Owner Institution Admin or Partner Institution Admin
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR) {
            if (user.getInstitutionId() != null
                    && (user.getInstitutionId().equals(agreement.getOwnerInstitutionId())
                    || user.getInstitutionId().equals(agreement.getPartnerInstitutionId()))) {
                return;
            }
        }

        // Check if user is a grantee on this agreement
        boolean isGrantee = grantRepository.findByAgreementIdAndUserId(agreement.getId(), user.getId()).isPresent();
        if (isGrantee) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view usage fees for this agreement");
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

