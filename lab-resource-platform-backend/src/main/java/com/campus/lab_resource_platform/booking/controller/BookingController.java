package com.campus.lab_resource_platform.booking.controller;

import com.campus.lab_resource_platform.booking.dto.*;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.service.BookingAutoScheduler;
import com.campus.lab_resource_platform.booking.service.BookingService;
import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Set<User.Role> COLLEGE_STAFF_ROLES = Set.of(
            User.Role.INSTITUTION_ADMINISTRATOR,
            User.Role.DEPARTMENT_HEAD,
            User.Role.LAB_MANAGER,
            User.Role.LAB_TECHNICIAN
    );

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final BookingAutoScheduler bookingAutoScheduler;

    public BookingController(
            BookingService bookingService,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            CollegeRepository collegeRepository,
            BookingAutoScheduler bookingAutoScheduler) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.bookingAutoScheduler = bookingAutoScheduler;
    }

    public record BookingListItemDto(
            Long id,
            Long equipmentId,
            String equipmentName,
            Long collegeId,
            String collegeName,
            Long userId,
            String userName,
            String userEmail,
            Instant startTime,
            Instant endTime,
            String purpose,
            String status,
            Instant approvedAt,
            String rejectionReason,
            Instant cancelledAt,
            String cancellationReason,
            Instant createdAt
    ) {}

    public record UpdateStatusRequest(
            String status,
            String reason
    ) {}

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    /** Staff/admin endpoint: get bookings strictly filtered by college for staff */
    @GetMapping
    public ResponseEntity<List<BookingListItemDto>> getAllBookings(
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) String status,
            Authentication authentication
    ) {
        // Run lifecycle check right away so returned list is 100% current to this exact moment
        try {
            bookingAutoScheduler.processBookingLifecycle();
        } catch (Exception e) {
            // Ignore to allow query to proceed
        }

        User currentUser = getAuthenticatedUser(authentication);
        final Long enforcedCollegeId;

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            enforcedCollegeId = currentUser.getCollegeId() != null ? currentUser.getCollegeId() : currentUser.getInstitutionId();
            if (enforcedCollegeId == null) {
                // Staff member without assigned college cannot see any bookings
                return ResponseEntity.ok(List.of());
            }
        } else {
            // SYSTEM_ADMINISTRATOR: allow custom collegeId filtering or null for all
            enforcedCollegeId = collegeId;
        }

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        List<Booking> bookings = bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        List<BookingListItemDto> dtos = bookings.stream()
                .filter(b -> {
                    if (enforcedCollegeId == null) return true;
                    Long equipCollegeId = b.getEquipment() != null ? b.getEquipment().getCollegeId() : null;
                    if (equipCollegeId == null && b.getEquipment() != null) {
                        equipCollegeId = b.getEquipment().getInstitutionId();
                    }
                    if (equipCollegeId == null && b.getUser() != null) {
                        equipCollegeId = b.getUser().getCollegeId();
                    }
                    return enforcedCollegeId.equals(equipCollegeId);
                })
                .filter(b -> {
                    if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) return true;
                    return b.getStatus() != null && b.getStatus().name().equalsIgnoreCase(status.trim());
                })
                .map(b -> {
                    Long cId = b.getEquipment() != null ? b.getEquipment().getCollegeId() : null;
                    if (cId == null && b.getEquipment() != null) {
                        cId = b.getEquipment().getInstitutionId();
                    }
                    if (cId == null && b.getUser() != null) {
                        cId = b.getUser().getCollegeId();
                    }
                    String collegeName = (cId != null && collegeMap.containsKey(cId))
                            ? collegeMap.get(cId).getName()
                            : "General";

                    return new BookingListItemDto(
                            b.getId(),
                            b.getEquipment() != null ? b.getEquipment().getId() : null,
                            b.getEquipment() != null ? b.getEquipment().getName() : null,
                            cId,
                            collegeName,
                            b.getUser() != null ? b.getUser().getId() : null,
                            b.getUser() != null ? b.getUser().getFullName() : null,
                            b.getUser() != null ? b.getUser().getEmail() : null,
                            b.getStartTime(),
                            b.getEndTime(),
                            b.getPurpose(),
                            b.getStatus() != null ? b.getStatus().name() : null,
                            b.getApprovedAt(),
                            b.getRejectionReason(),
                            b.getCancelledAt(),
                            b.getCancellationReason(),
                            b.getCreatedAt()
                    );
                })
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /** Directly update booking status (for staff roles) with college boundary check */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest body,
            Authentication authentication
    ) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (body == null || body.status() == null || body.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        User currentUser = getAuthenticatedUser(authentication);
        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = currentUser.getCollegeId() != null ? currentUser.getCollegeId() : currentUser.getInstitutionId();
            Long bookingCollegeId = booking.getEquipment() != null ? booking.getEquipment().getCollegeId() : null;
            if (bookingCollegeId == null && booking.getEquipment() != null) {
                bookingCollegeId = booking.getEquipment().getInstitutionId();
            }
            if (staffCollegeId == null || !staffCollegeId.equals(bookingCollegeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. You can only manage booking requests for your college's equipment.");
            }
        }

        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(body.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking status: " + body.status());
        }

        booking.setStatus(newStatus);

        if (newStatus == BookingStatus.CONFIRMED) {
            booking.setApprover(currentUser);
            booking.setApprovedAt(Instant.now());
        } else if (newStatus == BookingStatus.CANCELLED) {
            booking.setCancelledAt(Instant.now());
            if (body.reason() != null && !body.reason().isBlank()) {
                booking.setCancellationReason(body.reason().trim());
                booking.setRejectionReason(body.reason().trim());
            }
        }

        bookingRepository.save(booking);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.createBooking(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Student/researcher: get their own bookings, enriched with college name */
    @GetMapping("/my")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication
    ) {
        // Run lifecycle check right away so returned list is 100% current to this exact moment
        try {
            bookingAutoScheduler.processBookingLifecycle();
        } catch (Exception e) {
            // Ignore
        }

        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<BookingResponse> responsePage = bookingService.getMyBookings(authentication.getName(), status, pageable);

        // Enrich with college name
        Map<Long, String> collegeNameMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, College::getName, (a, b) -> a));

        List<BookingResponse> enriched = responsePage.getContent().stream().map(br -> {
            if (br.getCollegeId() != null) {
                br.setCollegeName(collegeNameMap.getOrDefault(br.getCollegeId(), null));
            }
            return br;
        }).toList();

        Page<BookingResponse> enrichedPage = new PageImpl<>(enriched, pageable, responsePage.getTotalElements());
        return ResponseEntity.ok(enrichedPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.getBookingById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /** Student cancel - body is optional (reason not required) */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request,
            Authentication authentication
    ) {
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Cancelled by student";
        BookingResponse response = bookingService.cancelBooking(id, reason, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<BookingResponse> approveBooking(
            @PathVariable Long id,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.approveBooking(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<BookingResponse> rejectBooking(
            @PathVariable Long id,
            @Valid @RequestBody RejectBookingRequest request,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.rejectBooking(id, request.getReason(), authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<BookingHistoryResponse>> getBookingHistory(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<BookingHistoryResponse> history = bookingService.getBookingHistory(id, authentication.getName());
        return ResponseEntity.ok(history);
    }
}
