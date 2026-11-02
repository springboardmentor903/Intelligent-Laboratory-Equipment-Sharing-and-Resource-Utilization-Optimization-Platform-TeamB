package com.campus.lab_resource_platform.utilization.controller;

import com.campus.lab_resource_platform.booking.service.BookingAutoScheduler;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.*;
import com.campus.lab_resource_platform.utilization.service.UtilizationService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/utilization")
public class UtilizationController {

    private static final Set<User.Role> COLLEGE_STAFF_ROLES = Set.of(
            User.Role.INSTITUTION_ADMINISTRATOR,
            User.Role.DEPARTMENT_HEAD,
            User.Role.LAB_MANAGER,
            User.Role.LAB_TECHNICIAN
    );

    private final UtilizationService utilizationService;
    private final UserRepository userRepository;
    private final BookingAutoScheduler bookingAutoScheduler;

    public UtilizationController(
            UtilizationService utilizationService,
            UserRepository userRepository,
            @Lazy BookingAutoScheduler bookingAutoScheduler) {
        this.utilizationService = utilizationService;
        this.userRepository = userRepository;
        this.bookingAutoScheduler = bookingAutoScheduler;
    }

    /**
     * Start session / Check-in
     */
    @PostMapping("/check-in")
    public ResponseEntity<UtilizationResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication
    ) {
        UtilizationResponse response = utilizationService.checkIn(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Stop session / Check-out by utilization ID
     */
    @PostMapping("/{id}/check-out")
    public ResponseEntity<UtilizationResponse> checkOut(
            @PathVariable Long id,
            @RequestBody(required = false) CheckOutRequest request,
            Authentication authentication
    ) {
        UtilizationResponse response = utilizationService.checkOut(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Stop session / Check-out directly by booking ID
     */
    @PostMapping("/booking/{bookingId}/check-out")
    public ResponseEntity<UtilizationResponse> checkOutByBooking(
            @PathVariable Long bookingId,
            @RequestBody(required = false) CheckOutRequest request,
            Authentication authentication
    ) {
        UtilizationResponse response = utilizationService.checkOutByBooking(bookingId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Real-time Live Status for all equipment (college-scoped for staff)
     */
    @GetMapping("/live-status")
    public ResponseEntity<List<EquipmentLiveStatusDto>> getLiveStatus(
            @RequestParam(required = false) Long collegeId,
            Authentication authentication
    ) {
        try {
            bookingAutoScheduler.processBookingLifecycle();
        } catch (Exception ignored) {}

        Long targetCollegeId = resolveCollegeId(collegeId, authentication);
        return ResponseEntity.ok(utilizationService.getLiveEquipmentStatuses(targetCollegeId));
    }

    /**
     * Utilization Summary & Statistics
     */
    @GetMapping("/summary")
    public ResponseEntity<UtilizationSummaryDto> getSummary(
            @RequestParam(required = false) Long collegeId,
            Authentication authentication
    ) {
        try {
            bookingAutoScheduler.processBookingLifecycle();
        } catch (Exception ignored) {}

        Long targetCollegeId = resolveCollegeId(collegeId, authentication);
        return ResponseEntity.ok(utilizationService.getUtilizationSummary(targetCollegeId));
    }

    /**
     * Equipment Usage History Logs
     */
    @GetMapping("/equipment/{equipmentId}/logs")
    public ResponseEntity<List<UtilizationResponse>> getEquipmentLogs(@PathVariable Long equipmentId) {
        return ResponseEntity.ok(utilizationService.getEquipmentUsageHistory(equipmentId));
    }

    /**
     * My Personal Usage Logs
     */
    @GetMapping("/my-logs")
    public ResponseEntity<List<UtilizationResponse>> getMyLogs(Authentication authentication) {
        return ResponseEntity.ok(utilizationService.getMyUsageHistory(authentication.getName()));
    }

    private Long resolveCollegeId(Long requestedCollegeId, Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            User user = userRepository.findByEmail(authentication.getName()).orElse(null);
            if (user != null && COLLEGE_STAFF_ROLES.contains(user.getRole())) {
                return user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
            }
        }
        return requestedCollegeId;
    }
}
