package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.dto.AvailabilitySlotResponse;
import com.campus.lab_resource_platform.booking.dto.BookingHistoryResponse;
import com.campus.lab_resource_platform.booking.dto.BookingRequest;
import com.campus.lab_resource_platform.booking.dto.BookingResponse;
import com.campus.lab_resource_platform.booking.dto.RecurringBookingRequest;
import com.campus.lab_resource_platform.booking.dto.RecurringBookingResponse;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingHistory;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.BookingConflictException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.sharing.event.ReleasedTimeEvent;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING_APPROVAL,
            BookingStatus.CONFIRMED,
            BookingStatus.IN_USE
    );

    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository bookingHistoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;
    private final ExternalAccessValidator accessValidator;
    private final SharedOccupancyService occupancyService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            @Lazy WaitlistService waitlistService,
            ExternalAccessValidator accessValidator,
            SharedOccupancyService occupancyService,
            ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.waitlistService = waitlistService;
        this.accessValidator = accessValidator;
        this.occupancyService = occupancyService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public synchronized BookingResponse createBooking(BookingRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateTimeRange(request.getStartTime(), request.getEndTime());

        Equipment equipment = equipmentRepository.findByIdWithLock(request.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Equipment not found with ID: " + request.getEquipmentId()));
        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new BadRequestException("Equipment is not available for booking. Status: " + equipment.getStatus());
        }

        long requestedMinutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        if (equipment.getMaxBookingHours() > 0
                && requestedMinutes > equipment.getMaxBookingHours() * 60L) {
            throw new BadRequestException(
                    "Booking duration exceeds the maximum allowed limit of "
                            + equipment.getMaxBookingHours() + " hours");
        }
        if (occupancyService.isSlotOccupied(
                equipment.getId(), request.getStartTime(), request.getEndTime(), null)) {
            throw new BookingConflictException("The requested slot overlaps with an active booking or hold");
        }

        ExternalAccessValidator.ValidationResult access =
                accessValidator.validate(equipment, user, request.getStartTime(), request.getEndTime());
        BookingStatus status = BookingStatus.PENDING_APPROVAL;
        Booking booking = Booking.builder()
                .equipment(equipment)
                .user(user)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .purpose(request.getPurpose())
                .status(status)
                .external(access.isExternal())
                .sharingAgreementId(access.getSharingAgreementId())
                .feePerHour(access.getFeePerHour())
                .estimatedFee(access.getEstimatedFee())
                .isRecurring(false)
                .build();
        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, null, status, user,
                status == BookingStatus.PENDING_APPROVAL ? "Booking created, pending approval" : "Booking created");
        return toResponse(saved);
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        return createBooking(request, getAuthenticatedUser().getEmail());
    }

    @Transactional
    public RecurringBookingResponse createRecurringBooking(RecurringBookingRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateTimeRange(request.getStartTime(), request.getEndTime());

        Equipment equipment = equipmentRepository.findByIdWithLock(request.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Equipment not found with ID: " + request.getEquipmentId()));
        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new BadRequestException("Equipment is not available for booking. Status: " + equipment.getStatus());
        }
        long requestedMinutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        if (equipment.getMaxBookingHours() > 0
                && requestedMinutes > equipment.getMaxBookingHours() * 60L) {
            throw new BadRequestException(
                    "Booking duration exceeds the maximum allowed limit of "
                            + equipment.getMaxBookingHours() + " hours");
        }

        int occurrences = request.getOccurrences() != null && request.getOccurrences() > 0
                ? Math.min(request.getOccurrences(), 16)
                : 4;
        String recurrenceType = request.getRecurrenceType() != null
                && request.getRecurrenceType().equalsIgnoreCase("DAILY") ? "DAILY" : "WEEKLY";
        String recurrenceGroupId = UUID.randomUUID().toString();
        Duration slotLength = Duration.between(request.getStartTime(), request.getEndTime());
        List<BookingResponse> successful = new ArrayList<>();
        List<RecurringBookingResponse.RecurringConflictDto> conflicts = new ArrayList<>();

        for (int i = 0; i < occurrences; i++) {
            Instant occurrenceStart = recurrenceType.equals("DAILY")
                    ? request.getStartTime().plus(i, ChronoUnit.DAYS)
                    : request.getStartTime().plus(i * 7L, ChronoUnit.DAYS);
            Instant occurrenceEnd = occurrenceStart.plus(slotLength);
            if (occupancyService.isSlotOccupied(
                    equipment.getId(), occurrenceStart, occurrenceEnd, null)) {
                conflicts.add(RecurringBookingResponse.RecurringConflictDto.builder()
                        .occurrenceIndex(i + 1)
                        .requestedStart(occurrenceStart)
                        .requestedEnd(occurrenceEnd)
                        .conflictReason("Slot already reserved by another booking")
                        .build());
                continue;
            }

            ExternalAccessValidator.ValidationResult access =
                    accessValidator.validate(equipment, user, occurrenceStart, occurrenceEnd);
            BookingStatus status = BookingStatus.PENDING_APPROVAL;
            Booking booking = Booking.builder()
                    .equipment(equipment)
                    .user(user)
                    .startTime(occurrenceStart)
                    .endTime(occurrenceEnd)
                    .purpose((request.getPurpose() != null ? request.getPurpose() : "Recurring Session")
                            + " (Session #" + (i + 1) + ")")
                    .status(status)
                    .external(access.isExternal())
                    .sharingAgreementId(access.getSharingAgreementId())
                    .feePerHour(access.getFeePerHour())
                    .estimatedFee(access.getEstimatedFee())
                    .isRecurring(true)
                    .recurrenceGroupId(recurrenceGroupId)
                    .recurrenceType(recurrenceType)
                    .build();
            Booking saved = bookingRepository.save(booking);
            recordHistory(saved, null, status, user,
                    "Recurring booking session #" + (i + 1) + " created");
            successful.add(toResponse(saved));
        }

        return RecurringBookingResponse.builder()
                .recurrenceGroupId(recurrenceGroupId)
                .recurrenceType(recurrenceType)
                .totalRequested(occurrences)
                .totalBooked(successful.size())
                .totalConflicted(conflicts.size())
                .successfulBookings(successful)
                .conflicts(conflicts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getRecurringSeries(String recurrenceGroupId, String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Booking> series = bookingRepository.findByRecurrenceGroupId(recurrenceGroupId);
        if (series.stream().anyMatch(booking -> !isOwnerOrManager(user, booking))) {
            throw new ForbiddenException("You are not authorized to view this recurring series");
        }
        return series.stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<BookingResponse> cancelRecurringSeries(
            String recurrenceGroupId, String reason, String currentUserEmail) {
        User user = getUserByEmail(currentUserEmail);
        List<Booking> series = bookingRepository.findByRecurrenceGroupId(recurrenceGroupId);
        if (series.isEmpty()) {
            throw new ResourceNotFoundException("No recurring bookings found for group: " + recurrenceGroupId);
        }
        List<BookingResponse> cancelled = new ArrayList<>();
        for (Booking booking : series) {
            if (!isOwnerOrManager(user, booking)) {
                throw new ForbiddenException("You are not authorized to cancel this recurring series");
            }
            if (booking.getStatus() != BookingStatus.PENDING_APPROVAL
                    && booking.getStatus() != BookingStatus.CONFIRMED) {
                continue;
            }
            BookingStatus previousStatus = booking.getStatus();
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(Instant.now());
            booking.setCancellationReason(reason != null ? reason : "Recurring series cancelled");
            Booking saved = bookingRepository.save(booking);
            recordHistory(saved, previousStatus, BookingStatus.CANCELLED, user,
                    "Recurring series cancellation: " + reason);
            releaseSlot(saved);
            cancelled.add(toResponse(saved));
        }
        return cancelled;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(
            String userEmail, BookingStatus status, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        Page<Booking> bookings = status == null
                ? bookingRepository.findByUserId(user.getId(), pageable)
                : bookingRepository.findByUserIdAndStatus(user.getId(), status, pageable);
        return bookings.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(Pageable pageable) {
        User user = getAuthenticatedUser();
        return bookingRepository.findByUserIdOrderByStartTimeDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id, String currentUserEmail) {
        Booking booking = getBookingEntity(id);
        User user = getUserByEmail(currentUserEmail);
        if (!isOwnerOrManager(user, booking)) {
            throw new ForbiddenException("You are not authorized to view this booking");
        }
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        return getBookingById(id, getAuthenticatedUser().getEmail());
    }

    @Transactional
    public BookingResponse cancelBooking(Long id, String reason, String currentUserEmail) {
        Booking booking = getBookingEntity(id);
        User user = getUserByEmail(currentUserEmail);
        if (!isOwnerOrManager(user, booking)) {
            throw new ForbiddenException("You are not authorized to cancel this booking");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.IN_USE) {
            throw new BadRequestException("Cannot cancel a booking that is " + booking.getStatus());
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason != null ? reason : "Cancelled by user");
        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CANCELLED, user, "Booking cancelled: " + reason);
        releaseSlot(saved);
        return toResponse(saved);
    }

    @Transactional
    public BookingResponse cancelBooking(Long id, String reason) {
        return cancelBooking(id, reason, getAuthenticatedUser().getEmail());
    }

    @Transactional
    public BookingResponse approveBooking(Long id, String approverEmail) {
        Booking booking = getBookingEntity(id);
        User approver = getUserByEmail(approverEmail);
        if (!isAuthorizedManager(approver, booking.getEquipment())) {
            throw new ForbiddenException("You are not authorized to approve bookings for this equipment");
        }
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only pending bookings can be approved");
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setApprover(approver);
        booking.setApprovedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CONFIRMED, approver, "Approved by manager");
        return toResponse(saved);
    }

    @Transactional
    public BookingResponse rejectBooking(Long id, String reason, String approverEmail) {
        Booking booking = getBookingEntity(id);
        User approver = getUserByEmail(approverEmail);
        if (!isAuthorizedManager(approver, booking.getEquipment())) {
            throw new ForbiddenException("You are not authorized to reject bookings for this equipment");
        }
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only pending bookings can be rejected");
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setApprover(approver);
        booking.setRejectionReason(reason);
        booking.setApprovedAt(Instant.now());
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason);
        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CANCELLED, approver, "Rejected by manager: " + reason);
        releaseSlot(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BookingHistoryResponse> getBookingHistory(Long id, String currentUserEmail) {
        Booking booking = getBookingEntity(id);
        User user = getUserByEmail(currentUserEmail);
        if (!isOwnerOrManager(user, booking)) {
            throw new ForbiddenException("You are not authorized to view this booking's history");
        }
        return bookingHistoryRepository.findByBookingIdOrderByTimestampAsc(id)
                .stream()
                .map(BookingHistoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getEquipmentAvailability(
            Long equipmentId, Instant startDate, Instant endDate) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new ResourceNotFoundException("Equipment not found with ID: " + equipmentId);
        }
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new BadRequestException("Start date and end date are required, and end date must be after start date");
        }
        return bookingRepository.findOccupiedSlots(equipmentId, startDate, endDate, BLOCKING_STATUSES)
                .stream()
                .map(booking -> AvailabilitySlotResponse.builder()
                        .startTime(booking.getStartTime())
                        .endTime(booking.getEndTime())
                        .status(booking.getStatus())
                        .build())
                .toList();
    }

    public BookingResponse toResponse(Booking booking) {
        return BookingResponse.fromEntity(booking);
    }

    private void validateTimeRange(Instant start, Instant end) {
        if (start == null || end == null || !start.isAfter(Instant.now())) {
            throw new BadRequestException("Booking start time must be in the future");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("Booking end time must be after start time");
        }
    }

    private void releaseSlot(Booking booking) {
        if (booking.getEquipment() == null) {
            return;
        }
        waitlistService.checkAndPromoteWaitlistedCandidate(
                booking.getEquipment().getId(), booking.getStartTime(), booking.getEndTime());
        eventPublisher.publishEvent(new ReleasedTimeEvent(
                this,
                booking.getEquipment().getId(),
                booking.getId(),
                booking.getStartTime().atZone(IST_ZONE).toLocalDateTime(),
                booking.getEndTime().atZone(IST_ZONE).toLocalDateTime(),
                booking.getCancellationReason()
        ));
    }

    private void recordHistory(
            Booking booking, BookingStatus previousStatus, BookingStatus newStatus, User changedBy, String remarks) {
        BookingHistory history = BookingHistory.builder()
                .booking(booking)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .timestamp(Instant.now())
                .remarks(remarks)
                .build();
        bookingHistoryRepository.save(history);
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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("User authentication invalid or not found"));
    }

    private Booking getBookingEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
    }

    private boolean isOwnerOrManager(User user, Booking booking) {
        return booking.getUser() != null && booking.getUser().getId().equals(user.getId())
                || isAuthorizedManager(user, booking.getEquipment());
    }

    private boolean isAuthorizedManager(User user, Equipment equipment) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return true;
        }
        boolean managerRole = user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                || user.getRole() == User.Role.LAB_MANAGER
                || user.getRole() == User.Role.DEPARTMENT_HEAD
                || user.getRole() == User.Role.LAB_TECHNICIAN;
        if (!managerRole || equipment == null) {
            return false;
        }
        Long userCollegeId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        Long equipmentCollegeId = equipment.getCollegeId() != null
                ? equipment.getCollegeId()
                : equipment.getInstitutionId();
        return userCollegeId != null && userCollegeId.equals(equipmentCollegeId);
    }
}
