package com.campus.lab_resource_platform.utilization.controller;

import com.campus.lab_resource_platform.booking.service.BookingAutoScheduler;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.CheckInRequest;
import com.campus.lab_resource_platform.utilization.dto.CheckOutRequest;
import com.campus.lab_resource_platform.utilization.dto.EquipmentLiveStatusDto;
import com.campus.lab_resource_platform.utilization.dto.UtilizationResponse;
import com.campus.lab_resource_platform.utilization.dto.UtilizationSummaryDto;
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

    @PostMapping("/check-in")
    public ResponseEntity<UtilizationResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(utilizationService.checkIn(request, authentication.getName()));
    }

    @PostMapping("/check-out")
    public ResponseEntity<UtilizationResponse> checkOut(
            @RequestBody CheckOutRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(utilizationService.checkOut(request, authentication.getName()));
    }

    @PostMapping("/{id}/check-out")
    public ResponseEntity<UtilizationResponse> checkOutByUtilization(
            @PathVariable Long id,
            @RequestBody(required = false) CheckOutRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(utilizationService.checkOut(id, request, authentication.getName()));
    }

    @PostMapping("/booking/{bookingId}/check-out")
    public ResponseEntity<UtilizationResponse> checkOutByBooking(
            @PathVariable Long bookingId,
            @RequestBody(required = false) CheckOutRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                utilizationService.checkOutByBooking(bookingId, request, authentication.getName()));
    }

    @GetMapping("/live-status")
    public ResponseEntity<List<EquipmentLiveStatusDto>> getLiveStatus(
            @RequestParam(required = false) Long collegeId,
            Authentication authentication) {
        bookingAutoScheduler.processBookingLifecycle();
        return ResponseEntity.ok(
                utilizationService.getLiveEquipmentStatuses(resolveCollegeId(collegeId, authentication)));
    }

    @GetMapping("/summary")
    public ResponseEntity<UtilizationSummaryDto> getSummary(
            @RequestParam(required = false) Long collegeId,
            Authentication authentication) {
        bookingAutoScheduler.processBookingLifecycle();
        return ResponseEntity.ok(
                utilizationService.getUtilizationSummary(resolveCollegeId(collegeId, authentication)));
    }

    @GetMapping("/equipment/{equipmentId}/logs")
    public ResponseEntity<List<UtilizationResponse>> getEquipmentLogs(@PathVariable Long equipmentId) {
        return ResponseEntity.ok(utilizationService.getEquipmentUsageHistory(equipmentId));
    }

    @GetMapping("/my-logs")
    public ResponseEntity<List<UtilizationResponse>> getMyLogs(Authentication authentication) {
        return ResponseEntity.ok(utilizationService.getMyUsageHistory(authentication.getName()));
    }

    private Long resolveCollegeId(Long requestedCollegeId, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return requestedCollegeId;
        }
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user != null && COLLEGE_STAFF_ROLES.contains(user.getRole())) {
            return user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        }
        return requestedCollegeId;
    }
}
