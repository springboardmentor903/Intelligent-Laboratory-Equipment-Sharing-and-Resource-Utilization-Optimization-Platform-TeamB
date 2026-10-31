package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.dto.*;
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
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

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

    public BookingService(
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            @Lazy WaitlistService waitlistService
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.waitlistService = waitlistService;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        if (request.getStartTime().isBefore(Instant.now())) {
            throw new BadRequestException("Booking start time must be in the future");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("Booking end time must be after start time");
        }

        // Lock equipment row to prevent race conditions when two users book simultaneously
        Equipment equipment = equipmentRepository.findByIdWithLock(request.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + request.getEquipmentId()));

        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new BadRequestException("Equipment is currently not eligible for booking. Status: " + equipment.getStatus());
        }

        long durationHours = Duration.between(request.getStartTime(), request.getEndTime()).toHours();
        if (durationHours > equipment.getMaxBookingHours()) {
            throw new BadRequestException("Booking duration exceeds the maximum allowed limit of " + equipment.getMaxBookingHours() + " hours");
        }

        // Rule: New Start < Existing End AND New End > Existing Start
        List<Booking> conflicts = bookingRepository.findConflictingBookings(
                equipment.getId(),
                request.getStartTime(),
                request.getEndTime(),
                BLOCKING_STATUSES
        );

        if (!conflicts.isEmpty()) {
            throw new BookingConflictException("The requested slot overlaps with an active booking for this equipment");
        }

        // Student bookings ALWAYS default to PENDING_APPROVAL — no auto-confirm
        BookingStatus initialStatus = BookingStatus.PENDING_APPROVAL;

        Booking booking = Booking.builder()
                .equipment(equipment)
                .user(user)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .purpose(request.getPurpose())
                .status(initialStatus)
                .isRecurring(false)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        recordHistory(
                savedBooking,
                null,
                initialStatus,
                user,
                "Booking created, pending approval"
        );

        return BookingResponse.fromEntity(savedBooking);
    }

    /**
     * Recurring Booking Creation (Milestone 2 - Task T4)
     * Creates a series of bookings across days/weeks, linking them with recurrenceGroupId
     */
    @Transactional
    public RecurringBookingResponse createRecurringBooking(RecurringBookingRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        if (request.getStartTime().isBefore(Instant.now())) {
            throw new BadRequestException("First booking start time must be in the future");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("Booking end time must be after start time");
        }

        Equipment equipment = equipmentRepository.findByIdWithLock(request.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + request.getEquipmentId()));

        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new BadRequestException("Equipment is currently not eligible for booking. Status: " + equipment.getStatus());
        }

        long durationHours = Duration.between(request.getStartTime(), request.getEndTime()).toHours();
        if (durationHours > equipment.getMaxBookingHours()) {
            throw new BadRequestException("Booking duration exceeds maximum allowed limit of " + equipment.getMaxBookingHours() + " hours");
        }

        int occurrences = (request.getOccurrences() != null && request.getOccurrences() > 0)
                ? Math.min(request.getOccurrences(), 16) : 4;
        String recurrenceType = (request.getRecurrenceType() != null && request.getRecurrenceType().equalsIgnoreCase("DAILY"))
                ? "DAILY" : "WEEKLY";

        String recurrenceGroupId = UUID.randomUUID().toString();
        List<BookingResponse> successful = new ArrayList<>();
        List<RecurringBookingResponse.RecurringConflictDto> conflicts = new ArrayList<>();

        Duration slotLength = Duration.between(request.getStartTime(), request.getEndTime());

        for (int i = 0; i < occurrences; i++) {
            Instant occStart;
            if ("DAILY".equals(recurrenceType)) {
                occStart = request.getStartTime().plus(i, ChronoUnit.DAYS);
            } else {
                occStart = request.getStartTime().plus(i * 7L, ChronoUnit.DAYS);
            }
            Instant occEnd = occStart.plus(slotLength);

            // Check conflicts for this occurrence
            List<Booking> occConflicts = bookingRepository.findConflictingBookings(
                    equipment.getId(), occStart, occEnd, BLOCKING_STATUSES
            );

            if (!occConflicts.isEmpty()) {
                conflicts.add(RecurringBookingResponse.RecurringConflictDto.builder()
                        .occurrenceIndex(i + 1)
                        .requestedStart(occStart)
                        .requestedEnd(occEnd)
                        .conflictReason("Slot already reserved by another booking")
                        .build());
            } else {
                Booking booking = Booking.builder()
                        .equipment(equipment)
                        .user(user)
                        .startTime(occStart)
                        .endTime(occEnd)
                        .purpose((request.getPurpose() != null ? request.getPurpose() : "Recurring Session") + " (Session #" + (i + 1) + ")")
                        .status(BookingStatus.PENDING_APPROVAL)
                        .isRecurring(true)
                        .recurrenceGroupId(recurrenceGroupId)
                        .recurrenceType(recurrenceType)
                        .build();

                Booking saved = bookingRepository.save(booking);
                recordHistory(saved, null, BookingStatus.PENDING_APPROVAL, user,
                        "Recurring booking session #" + (i + 1) + " created, pending approval");
                successful.add(BookingResponse.fromEntity(saved));
            }
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
        return bookingRepository.findByRecurrenceGroupId(recurrenceGroupId)
                .stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional
    public List<BookingResponse> cancelRecurringSeries(String recurrenceGroupId, String reason, String currentUserEmail) {
        List<Booking> series = bookingRepository.findByRecurrenceGroupId(recurrenceGroupId);
        if (series.isEmpty()) {
            throw new ResourceNotFoundException("No recurring bookings found for group: " + recurrenceGroupId);
        }

        User user = getUserByEmail(currentUserEmail);
        List<BookingResponse> cancelled = new ArrayList<>();

        for (Booking b : series) {
            if (b.getStatus() == BookingStatus.PENDING_APPROVAL || b.getStatus() == BookingStatus.CONFIRMED) {
                BookingStatus prev = b.getStatus();
                b.setStatus(BookingStatus.CANCELLED);
                b.setCancelledAt(Instant.now());
                b.setCancellationReason(reason != null ? reason : "Recurring series cancelled");
                Booking saved = bookingRepository.save(b);
                recordHistory(saved, prev, BookingStatus.CANCELLED, user, "Recurring series cancellation: " + reason);
                cancelled.add(BookingResponse.fromEntity(saved));

                // Notify waitlist on freed slot
                if (b.getEquipment() != null) {
                    waitlistService.checkAndPromoteWaitlistedCandidate(b.getEquipment().getId(), b.getStartTime(), b.getEndTime());
                }
            }
        }
        return cancelled;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(String userEmail, BookingStatus status, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        Page<Booking> page = (status != null)
                ? bookingRepository.findByUserIdAndStatus(user.getId(), status, pageable)
                : bookingRepository.findByUserId(user.getId(), pageable);

        return page.map(BookingResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id, String currentUserEmail) {
        Booking booking = getBookingEntity(id);
        User user = getUserByEmail(currentUserEmail);

        boolean isOwner = booking.getUser().getId().equals(user.getId());
        boolean isManager = isAuthorizedManager(user, booking.getEquipment());

        if (!isOwner && !isManager) {
            throw new ForbiddenException("You are not authorized to view this booking");
        }

        return BookingResponse.fromEntity(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long id, String reason, String currentUserEmail) {
        Booking booking = getBookingEntity(id);
        User user = getUserByEmail(currentUserEmail);

        boolean isOwner = booking.getUser().getId().equals(user.getId());
        boolean isManager = isAuthorizedManager(user, booking.getEquipment());

        if (!isOwner && !isManager) {
            throw new ForbiddenException("You are not authorized to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a booking that is already " + booking.getStatus());
        }

        if (booking.getStatus() == BookingStatus.IN_USE) {
            throw new BadRequestException("Cannot cancel a booking that is currently IN USE. Please mark it as completed instead.");
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason != null ? reason : "Cancelled by user");

        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CANCELLED, user, "Booking cancelled: " + reason);

        // Auto-promote any waiting student for this newly released slot!
        if (saved.getEquipment() != null) {
            waitlistService.checkAndPromoteWaitlistedCandidate(
                    saved.getEquipment().getId(),
                    saved.getStartTime(),
                    saved.getEndTime()
            );
        }

        return BookingResponse.fromEntity(saved);
    }

    @Transactional
    public BookingResponse approveBooking(Long id, String approverEmail) {
        Booking booking = getBookingEntity(id);
        User approver = getUserByEmail(approverEmail);

        if (!isAuthorizedManager(approver, booking.getEquipment())) {
            throw new ForbiddenException("You are not authorized to approve bookings for this equipment");
        }

        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only bookings in PENDING_APPROVAL status can be approved. Current status: " + booking.getStatus());
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setApprover(approver);
        booking.setApprovedAt(Instant.now());

        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CONFIRMED, approver, "Approved by manager");

        return BookingResponse.fromEntity(saved);
    }

    @Transactional
    public BookingResponse rejectBooking(Long id, String reason, String approverEmail) {
        Booking booking = getBookingEntity(id);
        User approver = getUserByEmail(approverEmail);

        if (!isAuthorizedManager(approver, booking.getEquipment())) {
            throw new ForbiddenException("You are not authorized to reject bookings for this equipment");
        }

        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only bookings in PENDING_APPROVAL status can be rejected. Current status: " + booking.getStatus());
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setApprover(approver);
        booking.setRejectionReason(reason);
        booking.setApprovedAt(Instant.now());

        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CANCELLED, approver, "Rejected by manager: " + reason);

        // Auto-promote waitlist candidate for rejected slot
        if (saved.getEquipment() != null) {
            waitlistService.checkAndPromoteWaitlistedCandidate(
                    saved.getEquipment().getId(),
                    saved.getStartTime(),
                    saved.getEndTime()
            );
        }

        return BookingResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<BookingHistoryResponse> getBookingHistory(Long id, String currentUserEmail) {
        Booking booking = getBookingEntity(id);
        User user = getUserByEmail(currentUserEmail);

        boolean isOwner = booking.getUser().getId().equals(user.getId());
        boolean isManager = isAuthorizedManager(user, booking.getEquipment());

        if (!isOwner && !isManager) {
            throw new ForbiddenException("You are not authorized to view this booking's history");
        }

        return bookingHistoryRepository.findByBookingIdOrderByTimestampAsc(id)
                .stream()
                .map(BookingHistoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getEquipmentAvailability(Long equipmentId, Instant startDate, Instant endDate) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new ResourceNotFoundException("Equipment not found with ID: " + equipmentId);
        }

        if (!endDate.isAfter(startDate)) {
            throw new BadRequestException("End date must be after start date");
        }

        return bookingRepository.findOccupiedSlots(equipmentId, startDate, endDate, BLOCKING_STATUSES)
                .stream()
                .map(b -> AvailabilitySlotResponse.builder()
                        .startTime(b.getStartTime())
                        .endTime(b.getEndTime())
                        .status(b.getStatus())
                        .build())
                .toList();
    }

    private void recordHistory(Booking booking, BookingStatus previousStatus, BookingStatus newStatus, User changedBy, String remarks) {
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

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("User authentication invalid or not found"));
    }

    private Booking getBookingEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
    }

    private boolean isAuthorizedManager(User user, Equipment equipment) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return true;
        }

        boolean isManagerRole = user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR
                || user.getRole() == User.Role.LAB_MANAGER
                || user.getRole() == User.Role.DEPARTMENT_HEAD
                || user.getRole() == User.Role.LAB_TECHNICIAN;

        if (!isManagerRole) {
            return false;
        }

        Long userColId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        Long eqColId = equipment.getCollegeId() != null ? equipment.getCollegeId() : equipment.getInstitutionId();

        if (userColId == null || eqColId == null) {
            return false;
        }
        return userColId.equals(eqColId);
    }
}
