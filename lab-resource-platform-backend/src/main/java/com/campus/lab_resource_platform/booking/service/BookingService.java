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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

    public BookingService(
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
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

        BookingStatus initialStatus = equipment.isRequiresApproval()
                ? BookingStatus.PENDING_APPROVAL
                : BookingStatus.CONFIRMED;

        Booking booking = Booking.builder()
                .equipment(equipment)
                .user(user)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .purpose(request.getPurpose())
                .status(initialStatus)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        recordHistory(
                savedBooking,
                null,
                initialStatus,
                user,
                initialStatus == BookingStatus.PENDING_APPROVAL
                        ? "Booking created, pending manager approval"
                        : "Booking created and automatically confirmed"
        );

        return BookingResponse.fromEntity(savedBooking);
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

        if (booking.getStartTime().isBefore(Instant.now())) {
            throw new BadRequestException("Cannot cancel a booking that has already started");
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason);

        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CANCELLED, user, "Booking cancelled: " + reason);

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

        // Per requirements: Store status as CANCELLED and store rejection reason separately
        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setApprover(approver);
        booking.setRejectionReason(reason);
        booking.setApprovedAt(Instant.now());

        Booking saved = bookingRepository.save(booking);
        recordHistory(saved, previousStatus, BookingStatus.CANCELLED, approver, "Rejected by manager: " + reason);

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

        // Privacy: Return only time slots and status without user identities
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
                || user.getRole() == User.Role.DEPARTMENT_HEAD;

        if (!isManagerRole) {
            return false;
        }

        // Institutional boundary check
        return equipment.getInstitutionId() == null
                || (user.getInstitutionId() != null && user.getInstitutionId().equals(equipment.getInstitutionId()));
    }
}
