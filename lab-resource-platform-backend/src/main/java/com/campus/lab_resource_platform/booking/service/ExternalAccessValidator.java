package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.AccessGrantRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingListingRepository;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

@Service
public class ExternalAccessValidator {

    public static final int GENERAL_MAX_BOOKING_HOURS = 24;

    private final SharingListingRepository listingRepository;
    private final SharingAgreementRepository agreementRepository;
    private final AccessGrantRepository grantRepository;
    private final BookingRepository bookingRepository;
    private final OperatingHoursCapacityService capacityService;
    private final FeeCalculationService feeCalculationService;

    public ExternalAccessValidator(
            SharingListingRepository listingRepository,
            SharingAgreementRepository agreementRepository,
            AccessGrantRepository grantRepository,
            BookingRepository bookingRepository,
            OperatingHoursCapacityService capacityService,
            FeeCalculationService feeCalculationService) {
        this.listingRepository = listingRepository;
        this.agreementRepository = agreementRepository;
        this.grantRepository = grantRepository;
        this.bookingRepository = bookingRepository;
        this.capacityService = capacityService;
        this.feeCalculationService = feeCalculationService;
    }

    public static class ValidationResult {
        private final boolean external;
        private final Long sharingAgreementId;
        private final BigDecimal feePerHour;
        private final BigDecimal estimatedFee;

        public ValidationResult(boolean external, Long sharingAgreementId, BigDecimal feePerHour, BigDecimal estimatedFee) {
            this.external = external;
            this.sharingAgreementId = sharingAgreementId;
            this.feePerHour = feePerHour;
            this.estimatedFee = estimatedFee;
        }

        public boolean isExternal() { return external; }
        public Long getSharingAgreementId() { return sharingAgreementId; }
        public BigDecimal getFeePerHour() { return feePerHour; }
        public BigDecimal getEstimatedFee() { return estimatedFee; }
    }

    public ValidationResult validate(Equipment equipment, User user, LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }

        // Rule 1: Users without institution book normally without requiring agreement
        // Rule 2: Users from the same institution as equipment owner book normally
        boolean isInternal = (user.getInstitutionId() == null)
                || (equipment.getInstitutionId() != null && equipment.getInstitutionId().equals(user.getInstitutionId()));

        if (isInternal) {
            BigDecimal internalRate = equipment.getHourlyCost() != null ? equipment.getHourlyCost() : BigDecimal.ZERO;
            BigDecimal estimatedFee = feeCalculationService.calculateEstimatedFee(startTime, endTime, internalRate);
            return new ValidationResult(false, null, internalRate, estimatedFee);
        }

        // Rule 3: External user - must have active agreement and valid non-revoked grant
        LocalDate bookingStartDate = startTime.toLocalDate();
        LocalDate bookingEndDate = endTime.toLocalDate();

        List<SharingAgreement> activeAgreements = agreementRepository.findActiveAgreementsForUser(
                equipment.getId(),
                user.getId(),
                AgreementStatus.ACTIVE,
                GrantStatus.ACTIVE,
                LocalDate.now()
        );

        SharingAgreement validAgreement = null;
        for (SharingAgreement sa : activeAgreements) {
            // Rule 4: Entire booking must fall within agreement start and end dates
            if (!bookingStartDate.isBefore(sa.getStartDate()) && !bookingEndDate.isAfter(sa.getEndDate())) {
                validAgreement = sa;
                break;
            }
        }

        if (validAgreement == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No active sharing agreement or access grant covers this booking period"
            );
        }

        // Rule 5: Allowed hours check (if configured)
        if (validAgreement.getAllowedStartTime() != null && validAgreement.getAllowedEndTime() != null) {
            LocalTime allowedStart = validAgreement.getAllowedStartTime();
            LocalTime allowedEnd = validAgreement.getAllowedEndTime();

            LocalTime bookingStartLocal = startTime.toLocalTime();
            LocalTime bookingEndLocal = endTime.toLocalTime();

            if (bookingStartLocal.isBefore(allowedStart) || bookingEndLocal.isAfter(allowedEnd)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format("Booking outside allowed agreement hours: %s to %s", allowedStart, allowedEnd)
                );
            }
        }

        // Fetch equipment listing for terms
        SharingListing listing = listingRepository.findByEquipmentId(equipment.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Equipment does not have an active sharing listing"
                ));

        if (!listing.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment sharing listing is currently inactive");
        }

        // Rule 6: Minimum booking notice hours
        LocalDateTime now = LocalDateTime.now();
        int minNotice = listing.getMinNoticeHours() != null ? listing.getMinNoticeHours() : 0;
        if (startTime.isBefore(now.plusHours(minNotice))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Booking violates minimum notice of %d hours", minNotice)
            );
        }

        // Rule 7: Maximum booking duration: stricter of listing max duration and general max duration
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        int listingMaxHours = listing.getMaxBookingDurationHours() != null ? listing.getMaxBookingDurationHours() : GENERAL_MAX_BOOKING_HOURS;
        int stricterMaxHours = Math.min(listingMaxHours, GENERAL_MAX_BOOKING_HOURS);
        long maxAllowedMinutes = stricterMaxHours * 60L;

        if (durationMinutes > maxAllowedMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Booking duration exceeds maximum allowed duration of %d hours", stricterMaxHours)
            );
        }

        // Rule 8: External share percentage enforced as weekly limit (Monday to Sunday)
        validateWeeklyExternalShareLimit(equipment, listing, startTime, endTime);

        // Copy fee from agreement, NOT listing fee
        BigDecimal agreementFee = validAgreement.getFeePerHour();
        BigDecimal estimatedFee = feeCalculationService.calculateEstimatedFee(startTime, endTime, agreementFee);

        return new ValidationResult(true, validAgreement.getId(), agreementFee, estimatedFee);
    }

    private void validateWeeklyExternalShareLimit(
            Equipment equipment,
            SharingListing listing,
            LocalDateTime newStart,
            LocalDateTime newEnd) {

        LocalDate bookingDate = newStart.toLocalDate();
        LocalDate weekMonday = bookingDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekSunday = bookingDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        LocalDateTime weekStartDateTime = weekMonday.atStartOfDay();
        LocalDateTime weekEndDateTime = weekSunday.atTime(LocalTime.MAX);

        // Weekly capacity from operating-hours capacity function
        long weeklyCapacityMinutes = capacityService.getWeeklyOperatingHoursCapacity(equipment, bookingDate);
        BigDecimal sharePercentage = listing.getExternalSharePercentage() != null
                ? listing.getExternalSharePercentage()
                : BigDecimal.ZERO;

        long allowedExternalMinutes = BigDecimal.valueOf(weeklyCapacityMinutes)
                .multiply(sharePercentage)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();

        // Include PENDING_APPROVAL, CONFIRMED, IN_USE, and COMPLETED external bookings
        Set<BookingStatus> countedStatuses = Set.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_USE,
                BookingStatus.COMPLETED
        );

        List<Booking> existingExternalBookings = bookingRepository.findExternalBookingsInPeriod(
                equipment.getId(), weekStartDateTime, weekEndDateTime, countedStatuses);

        LocalTime opStart = capacityService.getOperatingStartTime(equipment);
        LocalTime opEnd = capacityService.getOperatingEndTime(equipment);

        long alreadyBookedMinutes = 0L;
        for (Booking b : existingExternalBookings) {
            alreadyBookedMinutes += capacityService.calculateBookedMinutesInOperatingHours(
                    b.getStartTime(), b.getEndTime(), weekMonday, weekSunday, opStart, opEnd);
        }

        long newBookingMinutes = capacityService.calculateBookedMinutesInOperatingHours(
                newStart, newEnd, weekMonday, weekSunday, opStart, opEnd);

        if (alreadyBookedMinutes + newBookingMinutes > allowedExternalMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format("Weekly external share limit exceeded. Allowed: %d mins, Booked: %d mins, Requested: %d mins",
                            allowedExternalMinutes, alreadyBookedMinutes, newBookingMinutes)
            );
        }
    }
}

