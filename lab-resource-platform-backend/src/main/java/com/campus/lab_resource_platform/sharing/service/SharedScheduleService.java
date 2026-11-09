package com.campus.lab_resource_platform.sharing.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.service.OperatingHoursCapacityService;
import com.campus.lab_resource_platform.booking.service.SharedOccupancyService;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.institution.service.InstitutionService;
import com.campus.lab_resource_platform.sharing.dto.SharedScheduleResponse;
import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.SharingAgreementRepository;
import com.campus.lab_resource_platform.sharing.repository.SharingListingRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SharedScheduleService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final EquipmentRepository equipmentRepository;
    private final SharingListingRepository listingRepository;
    private final SharingAgreementRepository agreementRepository;
    private final SharedOccupancyService occupancyService;
    private final OperatingHoursCapacityService capacityService;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final InstitutionService institutionService;

    public SharedScheduleService(
            EquipmentRepository equipmentRepository,
            SharingListingRepository listingRepository,
            SharingAgreementRepository agreementRepository,
            SharedOccupancyService occupancyService,
            OperatingHoursCapacityService capacityService,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            InstitutionService institutionService) {
        this.equipmentRepository = equipmentRepository;
        this.listingRepository = listingRepository;
        this.agreementRepository = agreementRepository;
        this.occupancyService = occupancyService;
        this.capacityService = capacityService;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.institutionService = institutionService;
    }

    @Transactional(readOnly = true)
    public SharedScheduleResponse getSharedSchedule(Long equipmentId, LocalDate startDate, LocalDate endDate) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + equipmentId));

        User currentUser = findAuthenticatedUserOrNull();

        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(7);
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(LocalTime.MAX);

        SharedScheduleResponse response = new SharedScheduleResponse();
        response.setEquipmentId(equipment.getId());
        response.setEquipmentName(equipment.getName());
        response.setStartDate(startDate);
        response.setEndDate(endDate);

        // Can current user see partner institution details?
        // Requirement: Partner institution name for external slots, visible ONLY to the owner's Lab Manager and Institution Administrator.
        boolean canSeePartnerDetails = canViewPartnerInstitution(equipment, currentUser);

        // Query occupied slots using shared occupancy service (includes active holds)
        List<SharedOccupancyService.OccupancySlot> occupancySlots = occupancyService.getOccupiedSlots(
                equipment.getId(), rangeStart, rangeEnd);

        for (SharedOccupancyService.OccupancySlot slot : occupancySlots) {
            SharedScheduleResponse.OccupiedSlot outSlot = new SharedScheduleResponse.OccupiedSlot();
            outSlot.setSlotId(slot.getId());
            outSlot.setStartTime(slot.getStartTime());
            outSlot.setEndTime(slot.getEndTime());

            if ("HOLD".equals(slot.getSlotType())) {
                outSlot.setType("HOLD");
                outSlot.setPartnerInstitutionName(null);
            } else {
                outSlot.setType(slot.isExternal() ? "EXTERNAL" : "INTERNAL");
                if (slot.isExternal() && canSeePartnerDetails && slot.getPartnerInstitutionId() != null) {
                    outSlot.setPartnerInstitutionName(institutionService.getInstitutionName(slot.getPartnerInstitutionId()));
                } else {
                    outSlot.setPartnerInstitutionName(null);
                }
            }

            // Note: Never expose the name of the user who booked an external slot!
            response.getOccupiedSlots().add(outSlot);
        }

        // Active agreement's allowed booking windows for the logged-in user, where applicable
        if (currentUser != null) {
            List<SharingAgreement> userAgreements = agreementRepository.findActiveAgreementsForUser(
                    equipment.getId(), currentUser.getId(), AgreementStatus.ACTIVE, GrantStatus.ACTIVE, LocalDate.now());
            if (!userAgreements.isEmpty()) {
                SharingAgreement activeAg = userAgreements.get(0);
                SharedScheduleResponse.AllowedBookingWindow window = new SharedScheduleResponse.AllowedBookingWindow();
                window.setAgreementId(activeAg.getId());
                window.setStartDate(activeAg.getStartDate());
                window.setEndDate(activeAg.getEndDate());
                window.setAllowedStartTime(activeAg.getAllowedStartTime());
                window.setAllowedEndTime(activeAg.getAllowedEndTime());
                response.setAllowedBookingWindow(window);
            }
        }

        // Calculate weekly capacity and external minutes already used for the week of startDate
        Optional<SharingListing> listingOpt = listingRepository.findByEquipmentId(equipment.getId());
        if (listingOpt.isPresent()) {
            SharingListing listing = listingOpt.get();
            LocalDate monday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            long weeklyCapacityMinutes = capacityService.getWeeklyOperatingHoursCapacity(equipment, startDate);
            BigDecimal sharePercentage = listing.getExternalSharePercentage() != null
                    ? listing.getExternalSharePercentage()
                    : BigDecimal.ZERO;

            long weeklyLimitMinutes = BigDecimal.valueOf(weeklyCapacityMinutes)
                    .multiply(sharePercentage)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();

            response.setWeeklyExternalShareLimitMinutes(weeklyLimitMinutes);

            // Calculate external minutes already used / booked this week
            Set<BookingStatus> countedStatuses = Set.of(
                    BookingStatus.PENDING_APPROVAL,
                    BookingStatus.CONFIRMED,
                    BookingStatus.IN_USE,
                    BookingStatus.COMPLETED
            );
            List<Booking> weeklyBookings = bookingRepository.findExternalBookingsInPeriod(
                    equipment.getId(),
                    monday.atStartOfDay(IST_ZONE).toInstant(),
                    sunday.atTime(LocalTime.MAX).atZone(IST_ZONE).toInstant(),
                    countedStatuses
            );

            LocalTime opStart = capacityService.getOperatingStartTime(equipment);
            LocalTime opEnd = capacityService.getOperatingEndTime(equipment);

            long externalUsedMinutes = 0L;
            for (Booking b : weeklyBookings) {
                externalUsedMinutes += capacityService.calculateBookedMinutesInOperatingHours(
                        LocalDateTime.ofInstant(b.getStartTime(), IST_ZONE),
                        LocalDateTime.ofInstant(b.getEndTime(), IST_ZONE),
                        monday,
                        sunday,
                        opStart,
                        opEnd
                );
            }

            response.setWeeklyExternalMinutesUsed(externalUsedMinutes);
        }

        return response;
    }

    private boolean canViewPartnerInstitution(Equipment equipment, User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return true;
        }

        boolean isSameInstitution = user.getInstitutionId() != null
                && user.getInstitutionId().equals(equipment.getInstitutionId());

        if (!isSameInstitution) {
            return false;
        }

        return user.getRole() == User.Role.LAB_MANAGER
                || user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR;
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
