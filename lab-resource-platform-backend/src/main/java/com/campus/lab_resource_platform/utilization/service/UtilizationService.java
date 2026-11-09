package com.campus.lab_resource_platform.utilization.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.CheckInRequest;
import com.campus.lab_resource_platform.utilization.dto.CheckOutRequest;
import com.campus.lab_resource_platform.utilization.dto.EquipmentLiveStatusDto;
import com.campus.lab_resource_platform.utilization.dto.UtilizationResponse;
import com.campus.lab_resource_platform.utilization.dto.UtilizationSummaryDto;
import com.campus.lab_resource_platform.utilization.entity.UsageType;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import com.campus.lab_resource_platform.utilization.websocket.UtilizationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UtilizationService {

    private static final Logger log = LoggerFactory.getLogger(UtilizationService.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<User.Role> STAFF_ROLES = Set.of(
            User.Role.INSTITUTION_ADMINISTRATOR,
            User.Role.DEPARTMENT_HEAD,
            User.Role.LAB_MANAGER,
            User.Role.LAB_TECHNICIAN,
            User.Role.SYSTEM_ADMINISTRATOR
    );

    private final UtilizationRepository utilizationRepository;
    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final UtilizationWebSocketHandler webSocketHandler;
    private final FeeCalculationService feeCalculationService;

    @Autowired
    public UtilizationService(
            UtilizationRepository utilizationRepository,
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            CollegeRepository collegeRepository,
            UtilizationWebSocketHandler webSocketHandler,
            FeeCalculationService feeCalculationService) {
        this.utilizationRepository = utilizationRepository;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.webSocketHandler = webSocketHandler;
        this.feeCalculationService = feeCalculationService;
    }

    @Transactional
    public UtilizationResponse checkIn(CheckInRequest request) {
        return checkIn(request, getAuthenticatedUser().getEmail());
    }

    @Transactional
    public UtilizationResponse checkIn(CheckInRequest request, String userEmail) {
        if (request == null || (request.getBookingId() == null && request.getEquipmentId() == null)) {
            throw new BadRequestException("Either bookingId or equipmentId must be provided");
        }
        User user = getUserByEmail(userEmail);
        Booking booking = null;
        Equipment equipment;

        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Booking not found: " + request.getBookingId()));
            equipment = booking.getEquipment();
            authorizeBookingOperation(booking, user);
            if (booking.getStatus() == BookingStatus.CANCELLED
                    || booking.getStatus() == BookingStatus.REJECTED
                    || booking.getStatus() == BookingStatus.COMPLETED
                    || booking.getStatus() == BookingStatus.IN_USE) {
                throw new BadRequestException("Booking cannot be checked in from status " + booking.getStatus());
            }
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new BadRequestException("Booking must be approved before check-in");
            }
        } else {
            equipment = equipmentRepository.findById(request.getEquipmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Equipment not found: " + request.getEquipmentId()));
            Instant now = Instant.now();
            List<Booking> currentBookings = bookingRepository.findConflictingBookings(
                    equipment.getId(),
                    now.minus(Duration.ofMinutes(15)),
                    now.plus(Duration.ofMinutes(15)),
                    List.of(BookingStatus.CONFIRMED, BookingStatus.IN_USE)
            );
            booking = currentBookings.stream()
                    .filter(candidate -> candidate.getUser() != null
                            && candidate.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .orElse(null);
            if (booking != null) {
                authorizeBookingOperation(booking, user);
                if (booking.getStatus() == BookingStatus.IN_USE) {
                    throw new BadRequestException("Booking has already been checked in");
                }
            } else if (!STAFF_ROLES.contains(user.getRole())) {
                throw new ForbiddenException("An approved booking is required to check in to this equipment");
            }
        }

        if (equipment == null) {
            throw new BadRequestException("Booking does not have equipment assigned");
        }

        Instant now = Instant.now();
        Optional<Utilization> existingActive = utilizationRepository
                .findFirstByEquipmentIdAndStatus(equipment.getId(), "ACTIVE");
        if (existingActive.isPresent()) {
            Utilization active = existingActive.get();
            if (isStale(active, now)) {
                closeStaleSession(active, now);
            } else {
                throw new BadRequestException(
                        "Equipment '" + equipment.getName() + "' is already checked in by another session");
            }
        }

        Instant checkInTime = request.getCheckInTime() == null
                ? now
                : request.getCheckInTime().atZone(IST_ZONE).toInstant();
        Utilization utilization = Utilization.builder()
                .booking(booking)
                .equipment(equipment)
                .user(booking != null && booking.getUser() != null ? booking.getUser() : user)
                .usageType(booking != null && booking.isExternal() ? UsageType.SHARED : UsageType.INTERNAL)
                .checkInTime(checkInTime)
                .notes(request.getNotes())
                .operatorNotes(request.getNotes())
                .status("ACTIVE")
                .build();
        Utilization saved = utilizationRepository.save(utilization);

        if (booking != null) {
            booking.setStatus(BookingStatus.IN_USE);
            bookingRepository.save(booking);
        }
        equipment.setStatus(EquipmentStatus.IN_USE);
        if (equipmentRepository != null) {
            equipmentRepository.save(equipment);
        }

        UtilizationResponse response = toResponse(saved);
        broadcastLiveEvent("CHECK_IN", response);
        log.info("Check-in session #{} started for equipment #{} by user {}",
                saved.getId(), equipment.getId(), user.getEmail());
        return response;
    }

    @Transactional
    public UtilizationResponse checkOut(CheckOutRequest request) {
        return checkOut(request, getAuthenticatedUser().getEmail());
    }

    @Transactional
    public UtilizationResponse checkOut(CheckOutRequest request, String userEmail) {
        if (request == null || request.getBookingId() == null) {
            throw new BadRequestException("Booking ID is required");
        }
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.getBookingId()));
        User user = getUserByEmail(userEmail);
        authorizeBookingOperation(booking, user);
        Utilization utilization = utilizationRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new BadRequestException("No check-in record found for this booking"));
        return completeSession(utilization, request);
    }

    @Transactional
    public UtilizationResponse checkOut(Long utilizationId, CheckOutRequest request, String userEmail) {
        Utilization utilization = utilizationRepository.findById(utilizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilization session not found: " + utilizationId));
        if (!"ACTIVE".equalsIgnoreCase(utilization.getStatus())) {
            throw new BadRequestException(
                    "Session #" + utilizationId + " is already closed with status: " + utilization.getStatus());
        }
        User user = getUserByEmail(userEmail);
        if (utilization.getBooking() != null) {
            authorizeBookingOperation(utilization.getBooking(), user);
        } else if (!STAFF_ROLES.contains(user.getRole())) {
            throw new ForbiddenException("You are not authorized to check out this session");
        }
        return completeSession(utilization, request);
    }

    @Transactional
    public UtilizationResponse checkOutByBooking(Long bookingId, CheckOutRequest request, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        User user = getUserByEmail(userEmail);
        authorizeBookingOperation(booking, user);

        Optional<Utilization> active = utilizationRepository.findFirstByBookingIdAndStatus(bookingId, "ACTIVE");
        if (active.isEmpty() && booking.getEquipment() != null) {
            active = utilizationRepository.findFirstByEquipmentIdAndStatus(
                    booking.getEquipment().getId(), "ACTIVE");
        }
        if (active.isPresent()) {
            return completeSession(active.get(), request);
        }

        Instant now = checkoutTime(request);
        Instant checkInTime = booking.getStartTime() != null && booking.getStartTime().isBefore(now)
                ? booking.getStartTime()
                : now.minusSeconds(180);
        Utilization utilization = Utilization.builder()
                .booking(booking)
                .equipment(booking.getEquipment())
                .user(booking.getUser() != null ? booking.getUser() : user)
                .usageType(booking.isExternal() ? UsageType.SHARED : UsageType.INTERNAL)
                .checkInTime(checkInTime)
                .status("ACTIVE")
                .operatorNotes(request != null && request.getNotes() != null
                        ? request.getNotes()
                        : "Completed session via check-out")
                .notes(request != null ? request.getNotes() : null)
                .build();
        Utilization saved = utilizationRepository.save(utilization);
        return completeSession(saved, request);
    }

    @Transactional(readOnly = true)
    public List<EquipmentLiveStatusDto> getLiveEquipmentStatuses(Long collegeId) {
        List<Equipment> equipment = equipmentRepository.findAll().stream()
                .filter(item -> collegeId == null || collegeId.equals(item.getCollegeId())
                        || collegeId.equals(item.getInstitutionId()))
                .toList();
        Map<Long, College> colleges = collegeRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(College::getId, item -> item, (a, b) -> a));

        Instant now = Instant.now();
        LocalDate today = LocalDate.now(IST_ZONE);
        Instant todayStart = today.atStartOfDay(IST_ZONE).toInstant();
        Instant weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(
                java.time.DayOfWeek.MONDAY)).atStartOfDay(IST_ZONE).toInstant();
        List<Utilization> history = utilizationRepository.findRecentUtilizations(weekStart);
        Map<Long, Long> todayMinutes = new HashMap<>();
        Map<Long, Long> weekMinutes = new HashMap<>();
        Map<Long, Long> completedCount = new HashMap<>();
        for (Utilization utilization : history) {
            if (utilization.getEquipment() == null || utilization.getCheckInTime() == null) {
                continue;
            }
            Long equipmentId = utilization.getEquipment().getId();
            long duration = durationMinutes(utilization);
            weekMinutes.merge(equipmentId, duration, Long::sum);
            if (!utilization.getCheckInTime().isBefore(todayStart)) {
                todayMinutes.merge(equipmentId, duration, Long::sum);
            }
            if ("COMPLETED".equalsIgnoreCase(utilization.getStatus())) {
                completedCount.merge(equipmentId, 1L, Long::sum);
            }
        }

        Map<Long, Utilization> activeByEquipment = new HashMap<>();
        for (Utilization utilization : utilizationRepository.findAllActiveSessions()) {
            if (utilization.getEquipment() != null) {
                activeByEquipment.putIfAbsent(utilization.getEquipment().getId(), utilization);
            }
        }

        return equipment.stream().map(item -> {
            Long collegeIdForEquipment = item.getCollegeId() != null
                    ? item.getCollegeId()
                    : item.getInstitutionId();
            College college = collegeIdForEquipment == null ? null : colleges.get(collegeIdForEquipment);
            Utilization active = activeByEquipment.get(item.getId());
            long elapsed = active == null ? 0
                    : Math.max(0, Duration.between(active.getCheckInTime(), now).toMinutes());
            long totalToday = todayMinutes.getOrDefault(item.getId(), 0L) + elapsed;
            long totalWeek = weekMinutes.getOrDefault(item.getId(), 0L) + elapsed;
            double todayRate = Math.min(100.0,
                    Math.round((totalToday / 720.0) * 1000.0) / 10.0);
            double weeklyRate = Math.min(100.0,
                    Math.round((totalWeek / (720.0 * 7.0)) * 1000.0) / 10.0);
            String category = item.getCategory() != null
                    ? item.getCategory().getName()
                    : item.getLegacyCategory();

            return EquipmentLiveStatusDto.builder()
                    .equipmentId(item.getId())
                    .equipmentName(item.getName())
                    .model(item.getModel())
                    .category(category)
                    .location(item.getLocation())
                    .collegeId(collegeIdForEquipment)
                    .collegeName(college != null ? college.getName() : "Affiliated College")
                    .status(item.getStatus())
                    .isCurrentlyInUse(active != null || item.getStatus() == EquipmentStatus.IN_USE)
                    .currentUtilizationId(active != null ? active.getId() : null)
                    .currentBookingId(active != null && active.getBooking() != null
                            ? active.getBooking().getId() : null)
                    .currentUserName(active != null && active.getUser() != null
                            ? active.getUser().getFullName() : null)
                    .currentUserEmail(active != null && active.getUser() != null
                            ? active.getUser().getEmail() : null)
                    .sessionStartTime(active != null ? active.getCheckInTime() : null)
                    .elapsedMinutes(elapsed)
                    .todayUtilizationRatePercent(todayRate)
                    .weeklyUtilizationRatePercent(weeklyRate)
                    .totalUsageMinutesToday(totalToday)
                    .totalSessionsCompleted(completedCount.getOrDefault(item.getId(), 0L))
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public UtilizationSummaryDto getUtilizationSummary(Long collegeId) {
        List<EquipmentLiveStatusDto> statuses = getLiveEquipmentStatuses(collegeId);
        long available = statuses.stream().filter(s -> s.getStatus() == EquipmentStatus.AVAILABLE).count();
        long inUse = statuses.stream().filter(s -> s.isCurrentlyInUse()
                || s.getStatus() == EquipmentStatus.IN_USE).count();
        long maintenance = statuses.stream()
                .filter(s -> s.getStatus() == EquipmentStatus.UNDER_MAINTENANCE).count();
        long decommissioned = statuses.stream()
                .filter(s -> s.getStatus() == EquipmentStatus.DECOMMISSIONED
                        || s.getStatus() == EquipmentStatus.RETIRED).count();
        double averageRate = statuses.isEmpty() ? 0.0
                : Math.round(statuses.stream()
                        .mapToDouble(EquipmentLiveStatusDto::getTodayUtilizationRatePercent)
                        .average().orElse(0.0) * 10.0) / 10.0;
        long totalTodayMinutes = statuses.stream()
                .mapToLong(EquipmentLiveStatusDto::getTotalUsageMinutesToday).sum();
        String collegeName = collegeId == null ? "All Colleges" : getCollegeName(collegeId);
        return UtilizationSummaryDto.builder()
                .collegeId(collegeId)
                .collegeName(collegeName)
                .totalEquipments(statuses.size())
                .availableCount(available)
                .inUseCount(inUse)
                .underMaintenanceCount(maintenance)
                .decommissionedCount(decommissioned)
                .overallUtilizationRatePercent(averageRate)
                .totalUsageMinutesToday(totalTodayMinutes)
                .activeSessionsCount(inUse)
                .equipments(statuses)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UtilizationResponse> getEquipmentUsageHistory(Long equipmentId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + equipmentId));
        String collegeName = getCollegeName(equipment);
        return utilizationRepository.findByEquipmentIdOrderByCheckInTimeDesc(equipmentId).stream()
                .map(utilization -> toResponse(utilization, collegeName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UtilizationResponse> getMyUsageHistory(String userEmail) {
        User user = getUserByEmail(userEmail);
        return utilizationRepository.findByUserIdOrderByCheckInTimeDesc(user.getId()).stream()
                .map(utilization -> toResponse(utilization, getCollegeName(utilization.getEquipment())))
                .toList();
    }

    private UtilizationResponse completeSession(Utilization utilization, CheckOutRequest request) {
        if (!"ACTIVE".equalsIgnoreCase(utilization.getStatus())
                && utilization.getCheckOutTime() != null) {
            throw new BadRequestException("Utilization session has already been checked out");
        }
        Instant checkOutTime = checkoutTime(request);
        if (utilization.getCheckInTime() != null && checkOutTime.isBefore(utilization.getCheckInTime())) {
            throw new BadRequestException("Check-out time cannot be before check-in time");
        }
        long minutes = utilization.getCheckInTime() == null
                ? 0
                : Math.max(0, Duration.between(utilization.getCheckInTime(), checkOutTime).toMinutes());
        int usedMinutes = (int) Math.min(minutes, Integer.MAX_VALUE);
        utilization.setCheckOutTime(checkOutTime);
        utilization.setUsedMinutes(usedMinutes);
        utilization.setActualDurationMinutes(minutes);
        utilization.setStatus("COMPLETED");
        appendNotes(utilization, request == null ? null : request.getNotes());

        Booking booking = utilization.getBooking();
        BigDecimal hourlyRate = booking != null && booking.getFeePerHour() != null
                ? booking.getFeePerHour()
                : utilization.getEquipment() != null && utilization.getEquipment().getHourlyCost() != null
                        ? utilization.getEquipment().getHourlyCost()
                        : BigDecimal.ZERO;
        BigDecimal actualFee = feeCalculationService.calculateActualFee(minutes, hourlyRate);
        utilization.setActualFee(actualFee);
        Utilization saved = utilizationRepository.save(utilization);

        if (booking != null && booking.getStatus() != BookingStatus.CANCELLED
                && booking.getStatus() != BookingStatus.REJECTED) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setActualFee(actualFee);
            bookingRepository.save(booking);
        }
        Equipment equipment = utilization.getEquipment();
        if (equipment != null && equipment.getStatus() == EquipmentStatus.IN_USE) {
            equipment.setStatus(EquipmentStatus.AVAILABLE);
            if (equipmentRepository != null) {
                equipmentRepository.save(equipment);
            }
        }

        UtilizationResponse response = toResponse(saved);
        broadcastLiveEvent("CHECK_OUT", response);
        log.info("Check-out completed for utilization session #{} with {} minutes used",
                saved.getId(), minutes);
        return response;
    }

    private Instant checkoutTime(CheckOutRequest request) {
        return request != null && request.getCheckOutTime() != null
                ? request.getCheckOutTime().atZone(IST_ZONE).toInstant()
                : Instant.now();
    }

    private void appendNotes(Utilization utilization, String notes) {
        if (notes == null || notes.isBlank()) {
            return;
        }
        utilization.setNotes(appendNote(utilization.getNotes(), notes));
        utilization.setOperatorNotes(appendNote(utilization.getOperatorNotes(), notes));
    }

    private String appendNote(String existing, String note) {
        return existing == null || existing.isBlank() ? note : existing + " | " + note;
    }

    private boolean isStale(Utilization utilization, Instant now) {
        Booking booking = utilization.getBooking();
        if (booking != null && (booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED)) {
            return true;
        }
        if (booking != null && booking.getEndTime() != null && !now.isBefore(booking.getEndTime())) {
            return true;
        }
        return utilization.getCheckInTime() != null
                && Duration.between(utilization.getCheckInTime(), now).toHours() >= 4;
    }

    private void closeStaleSession(Utilization utilization, Instant now) {
        long minutes = utilization.getCheckInTime() == null
                ? 0
                : Math.max(0, Duration.between(utilization.getCheckInTime(), now).toMinutes());
        utilization.setCheckOutTime(now);
        utilization.setActualDurationMinutes(minutes);
        utilization.setUsedMinutes((int) Math.min(minutes, Integer.MAX_VALUE));
        utilization.setStatus("COMPLETED");
        utilizationRepository.save(utilization);
    }

    private void authorizeBookingOperation(Booking booking, User user) {
        boolean owner = booking.getUser() != null && booking.getUser().getId().equals(user.getId());
        Equipment equipment = booking.getEquipment();
        boolean manager = equipment != null && STAFF_ROLES.contains(user.getRole())
                && (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR
                || sameInstitution(user, equipment));
        if (!owner && !manager) {
            throw new ForbiddenException("You are not authorized to operate this booking");
        }
    }

    private boolean sameInstitution(User user, Equipment equipment) {
        Long userCollegeId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        Long equipmentCollegeId = equipment.getCollegeId() != null
                ? equipment.getCollegeId()
                : equipment.getInstitutionId();
        return userCollegeId != null && userCollegeId.equals(equipmentCollegeId);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().equals("anonymousUser")) {
            throw new ForbiddenException("Authentication is required");
        }
        return getUserByEmail(authentication.getName());
    }

    private User getUserByEmail(String email) {
        if (email == null) {
            throw new BadRequestException("User email cannot be null");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        if (!user.isActive()) {
            throw new ForbiddenException("Inactive users cannot use equipment");
        }
        return user;
    }

    private UtilizationResponse toResponse(Utilization utilization) {
        return toResponse(utilization, getCollegeName(utilization.getEquipment()));
    }

    private UtilizationResponse toResponse(Utilization utilization, String collegeName) {
        return UtilizationResponse.fromEntity(utilization, collegeName);
    }

    private String getCollegeName(Equipment equipment) {
        if (equipment == null) {
            return "Affiliated College";
        }
        Long collegeId = equipment.getCollegeId() != null
                ? equipment.getCollegeId()
                : equipment.getInstitutionId();
        return collegeId == null ? "Affiliated College" : getCollegeName(collegeId);
    }

    private String getCollegeName(Long collegeId) {
        if (collegeRepository == null) {
            return "Affiliated College";
        }
        return collegeRepository.findById(collegeId)
                .map(College::getName)
                .orElse("Affiliated College");
    }

    private long durationMinutes(Utilization utilization) {
        if (utilization.getActualDurationMinutes() != null) {
            return utilization.getActualDurationMinutes();
        }
        if (utilization.getUsedMinutes() != null) {
            return utilization.getUsedMinutes();
        }
        if (utilization.getCheckInTime() != null && utilization.getCheckOutTime() != null) {
            return Math.max(0, Duration.between(
                    utilization.getCheckInTime(), utilization.getCheckOutTime()).toMinutes());
        }
        return 0;
    }

    private void broadcastLiveEvent(String eventType, UtilizationResponse data) {
        if (webSocketHandler == null) {
            return;
        }
        try {
            String json = String.format(
                    "{\"type\":\"%s\",\"equipmentId\":%s,\"utilizationId\":%s,\"status\":\"%s\",\"timestamp\":\"%s\"}",
                    eventType,
                    data.getEquipmentId(),
                    data.getId(),
                    data.getStatus(),
                    Instant.now()
            );
            webSocketHandler.broadcastEvent(json);
        } catch (RuntimeException e) {
            log.warn("Could not broadcast live utilization event: {}", e.getMessage(), e);
        }
    }
}
