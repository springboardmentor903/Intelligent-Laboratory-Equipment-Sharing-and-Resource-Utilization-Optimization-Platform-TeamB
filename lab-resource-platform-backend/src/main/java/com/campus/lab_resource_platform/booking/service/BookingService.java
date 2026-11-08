package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.dto.BookingRequest;
import com.campus.lab_resource_platform.booking.dto.BookingResponse;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.sharing.event.ReleasedTimeEvent;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final ExternalAccessValidator accessValidator;
    private final SharedOccupancyService occupancyService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            ExternalAccessValidator accessValidator,
            SharedOccupancyService occupancyService,
            ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.accessValidator = accessValidator;
        this.occupancyService = occupancyService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public synchronized BookingResponse createBooking(BookingRequest request) {
        User currentUser = getAuthenticatedUser();
        Equipment equipment = equipmentRepository.findById(request.getEquipmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();

        if (start.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking start time cannot be in the past");
        }
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }

        // Check occupancy conflicts
        if (occupancyService.isSlotOccupied(equipment.getId(), start, end, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected time slot is already occupied");
        }

        // Validate booking rules (including external sharing validations)
        ExternalAccessValidator.ValidationResult result = accessValidator.validate(equipment, currentUser, start, end);

        Booking booking = new Booking();
        booking.setEquipment(equipment);
        booking.setUser(currentUser);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setPurpose(request.getPurpose());
        booking.setExternal(result.isExternal());
        booking.setSharingAgreementId(result.getSharingAgreementId());
        booking.setFeePerHour(result.getFeePerHour());
        booking.setEstimatedFee(result.getEstimatedFee());

        // Status rule: PENDING_APPROVAL when equipment approval is required, CONFIRMED when not required
        boolean requiresApproval = Boolean.TRUE.equals(equipment.getRequiresApproval());
        booking.setStatus(requiresApproval ? BookingStatus.PENDING_APPROVAL : BookingStatus.CONFIRMED);

        Booking saved = bookingRepository.save(booking);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(Pageable pageable) {
        User currentUser = getAuthenticatedUser();
        return bookingRepository.findByUserIdOrderByStartTimeDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public BookingResponse cancelBooking(Long id, String reason) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));

        User currentUser = getAuthenticatedUser();
        if (!booking.getUser().getId().equals(currentUser.getId()) && currentUser.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel another user's booking");
        }

        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking cannot be cancelled in its current state");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason != null ? reason : "Cancelled by user");
        Booking saved = bookingRepository.save(booking);

        eventPublisher.publishEvent(new ReleasedTimeEvent(
                this,
                booking.getEquipment().getId(),
                booking.getId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getCancellationReason()
        ));

        return toResponse(saved);
    }

    public BookingResponse toResponse(Booking b) {
        BookingResponse resp = new BookingResponse();
        resp.setId(b.getId());
        resp.setEquipmentId(b.getEquipment().getId());
        resp.setEquipmentName(b.getEquipment().getName());
        resp.setAssetTag(b.getEquipment().getAssetTag());
        resp.setUserId(b.getUser().getId());
        resp.setUserName(b.getUser().getFullName());
        resp.setStartTime(b.getStartTime());
        resp.setEndTime(b.getEndTime());
        resp.setStatus(b.getStatus());
        resp.setExternal(b.isExternal());
        resp.setSharingAgreementId(b.getSharingAgreementId());
        resp.setFeePerHour(b.getFeePerHour());
        resp.setEstimatedFee(b.getEstimatedFee());
        resp.setActualFee(b.getActualFee());
        resp.setPurpose(b.getPurpose());
        resp.setRejectionReason(b.getRejectionReason());
        resp.setCancellationReason(b.getCancellationReason());
        resp.setCreatedAt(b.getCreatedAt());
        resp.setUpdatedAt(b.getUpdatedAt());
        return resp;
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

