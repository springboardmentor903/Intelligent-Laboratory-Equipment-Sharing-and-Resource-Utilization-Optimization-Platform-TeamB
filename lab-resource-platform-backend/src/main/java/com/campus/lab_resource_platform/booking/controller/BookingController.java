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
            Boolean isRecurring,
            String recurrenceGroupId,
            String recurrenceType,
            Instant createdAt
    ) {}

    @GetMapping
    public ResponseEntity<List<BookingListItemDto>> getAllBookings(
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Authentication authentication
    ) {
        // Enforce automatic IST cancellation/completion on fetch
        try {
            bookingAutoScheduler.processBookingLifecycle();
        } catch (Exception e) {
            // Ignore
        }

        User currentUser = null;
        if (authentication != null && authentication.getName() != null) {
            currentUser = userRepository.findByEmail(authentication.getName()).orElse(null);
        }

        final Long targetCollegeId;
        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = currentUser.getCollegeId() != null ? currentUser.getCollegeId() : currentUser.getInstitutionId();
            if (staffCollegeId == null) {
                return ResponseEntity.ok(List.of());
            }
            targetCollegeId = staffCollegeId;
        } else {
            targetCollegeId = collegeId;
        }

        List<Booking> list = bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        if (targetCollegeId != null) {
            list = list.stream().filter(b -> {
                if (b.getEquipment() == null) return false;
                Long eqCol = b.getEquipment().getCollegeId() != null
                        ? b.getEquipment().getCollegeId()
                        : b.getEquipment().getInstitutionId();
                return targetCollegeId.equals(eqCol);
            }).toList();
        }

        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                BookingStatus bs = BookingStatus.valueOf(status.trim().toUpperCase());
                list = list.stream().filter(b -> b.getStatus() == bs).toList();
            } catch (IllegalArgumentException ignored) {}
        }

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            list = list.stream().filter(b -> {
                boolean matchEq = b.getEquipment() != null && b.getEquipment().getName() != null
                        && b.getEquipment().getName().toLowerCase().contains(q);
                boolean matchUser = b.getUser() != null && (
                        (b.getUser().getFullName() != null && b.getUser().getFullName().toLowerCase().contains(q))
                                || (b.getUser().getEmail() != null && b.getUser().getEmail().toLowerCase().contains(q))
                );
                boolean matchPurpose = b.getPurpose() != null && b.getPurpose().toLowerCase().contains(q);
                return matchEq || matchUser || matchPurpose;
            }).toList();
        }

        List<BookingListItemDto> dtos = list.stream().map(b -> {
            Long eqColId = null;
            String eqColName = "Affiliated College";
            if (b.getEquipment() != null) {
                eqColId = b.getEquipment().getCollegeId() != null
                        ? b.getEquipment().getCollegeId()
                        : b.getEquipment().getInstitutionId();
                if (eqColId != null && collegeMap.containsKey(eqColId)) {
                    eqColName = collegeMap.get(eqColId).getName();
                }
            }
            return new BookingListItemDto(
                    b.getId(),
                    b.getEquipment() != null ? b.getEquipment().getId() : null,
                    b.getEquipment() != null ? b.getEquipment().getName() : "Unknown Instrument",
                    eqColId,
                    eqColName,
                    b.getUser() != null ? b.getUser().getId() : null,
                    b.getUser() != null ? b.getUser().getFullName() : "Unknown Student",
                    b.getUser() != null ? b.getUser().getEmail() : "",
                    b.getStartTime(),
                    b.getEndTime(),
                    b.getPurpose(),
                    b.getStatus() != null ? b.getStatus().name() : "PENDING_APPROVAL",
                    b.getApprovedAt(),
                    b.getRejectionReason(),
                    b.getCancelledAt(),
                    b.getCancellationReason(),
                    b.getIsRecurring(),
                    b.getRecurrenceGroupId(),
                    b.getRecurrenceType(),
                    b.getCreatedAt()
            );
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingListItemDto> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String newStatusStr = body != null ? body.get("status") : null;
        if (newStatusStr == null || newStatusStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status field is required");
        }

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with ID: " + id));

        User currentUser = null;
        if (authentication != null && authentication.getName() != null) {
            currentUser = userRepository.findByEmail(authentication.getName()).orElse(null);
        }

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = currentUser.getCollegeId() != null ? currentUser.getCollegeId() : currentUser.getInstitutionId();
            Long eqCollegeId = booking.getEquipment() != null
                    ? (booking.getEquipment().getCollegeId() != null ? booking.getEquipment().getCollegeId() : booking.getEquipment().getInstitutionId())
                    : null;
            if (staffCollegeId == null || !staffCollegeId.equals(eqCollegeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. You can only manage bookings for your college's equipment.");
            }
        }

        BookingStatus previousStatus = booking.getStatus();
        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(newStatusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + newStatusStr);
        }

        booking.setStatus(newStatus);
        if (newStatus == BookingStatus.CONFIRMED && previousStatus == BookingStatus.PENDING_APPROVAL) {
            booking.setApprover(currentUser);
            booking.setApprovedAt(Instant.now());
        } else if (newStatus == BookingStatus.CANCELLED) {
            booking.setCancelledAt(Instant.now());
            booking.setCancellationReason(body.getOrDefault("reason", "Cancelled via status dropdown"));
        }

        bookingRepository.save(booking);

        Long eqColId = null;
        String eqColName = "Affiliated College";
        if (booking.getEquipment() != null) {
            eqColId = booking.getEquipment().getCollegeId() != null
                    ? booking.getEquipment().getCollegeId()
                    : booking.getEquipment().getInstitutionId();
            if (eqColId != null) {
                College col = collegeRepository.findById(eqColId).orElse(null);
                if (col != null) eqColName = col.getName();
            }
        }

        return ResponseEntity.ok(new BookingListItemDto(
                booking.getId(),
                booking.getEquipment() != null ? booking.getEquipment().getId() : null,
                booking.getEquipment() != null ? booking.getEquipment().getName() : "Unknown Instrument",
                eqColId,
                eqColName,
                booking.getUser() != null ? booking.getUser().getId() : null,
                booking.getUser() != null ? booking.getUser().getFullName() : "Unknown Student",
                booking.getUser() != null ? booking.getUser().getEmail() : "",
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getPurpose(),
                booking.getStatus().name(),
                booking.getApprovedAt(),
                booking.getRejectionReason(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getIsRecurring(),
                booking.getRecurrenceGroupId(),
                booking.getRecurrenceType(),
                booking.getCreatedAt()
        ));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            Authentication authentication
    ) {
        BookingResponse response = bookingService.createBooking(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Recurring Booking creation endpoint (Milestone 2 - Task T4)
     */
    @PostMapping("/recurring")
    public ResponseEntity<RecurringBookingResponse> createRecurringBooking(
            @Valid @RequestBody RecurringBookingRequest request,
            Authentication authentication
    ) {
        RecurringBookingResponse response = bookingService.createRecurringBooking(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/series/{recurrenceGroupId}")
    public ResponseEntity<List<BookingResponse>> getRecurringSeries(
            @PathVariable String recurrenceGroupId,
            Authentication authentication
    ) {
        List<BookingResponse> series = bookingService.getRecurringSeries(recurrenceGroupId, authentication.getName());
        return ResponseEntity.ok(series);
    }

    @DeleteMapping("/series/{recurrenceGroupId}")
    public ResponseEntity<List<BookingResponse>> cancelRecurringSeries(
            @PathVariable String recurrenceGroupId,
            @RequestParam(required = false, defaultValue = "Recurring series cancelled") String reason,
            Authentication authentication
    ) {
        List<BookingResponse> cancelled = bookingService.cancelRecurringSeries(recurrenceGroupId, reason, authentication.getName());
        return ResponseEntity.ok(cancelled);
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
        try {
            bookingAutoScheduler.processBookingLifecycle();
        } catch (Exception e) {
            // Ignore
        }

        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<BookingResponse> responsePage = bookingService.getMyBookings(authentication.getName(), status, pageable);

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
