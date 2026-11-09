package com.campus.lab_resource_platform.booking.controller;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;

    public AdminBookingController(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            CollegeRepository collegeRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
    }

    public record AdminBookingDto(
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

    public record StatusReasonBody(String reason) {}

    private User requireAdministrator(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin account not found"));
        if (user.getRole() != User.Role.SYSTEM_ADMINISTRATOR || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only System Administrators can access this resource");
        }
        return user;
    }

    @GetMapping
    public List<AdminBookingDto> getAllBookings(
            @RequestParam(required = false) Long collegeId,
            @AuthenticationPrincipal String email) {
        requireAdministrator(email);

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        List<Booking> bookings = bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return bookings.stream()
                .filter(b -> {
                    if (collegeId == null) return true;
                    Long equipCollegeId = b.getEquipment() != null ? b.getEquipment().getCollegeId() : null;
                    if (equipCollegeId == null && b.getEquipment() != null) {
                        equipCollegeId = b.getEquipment().getInstitutionId();
                    }
                    if (equipCollegeId == null && b.getUser() != null) {
                        equipCollegeId = b.getUser().getCollegeId();
                    }
                    return collegeId.equals(equipCollegeId);
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

                    return new AdminBookingDto(
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
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {
        User admin = requireAdministrator(email);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setApprover(admin);
        booking.setApprovedAt(Instant.now());
        bookingRepository.save(booking);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectBooking(
            @PathVariable Long id,
            @RequestBody(required = false) StatusReasonBody body,
            @AuthenticationPrincipal String email) {
        User admin = requireAdministrator(email);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setApprover(admin);
        booking.setRejectionReason(body != null && body.reason() != null ? body.reason().trim() : "Rejected by system administrator");
        bookingRepository.save(booking);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) StatusReasonBody body,
            @AuthenticationPrincipal String email) {
        requireAdministrator(email);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(body != null && body.reason() != null ? body.reason().trim() : "Cancelled by system administrator");
        bookingRepository.save(booking);
        return ResponseEntity.noContent().build();
    }
}
