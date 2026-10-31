package com.campus.lab_resource_platform.user.controller;

import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.user.service.TechnicianAccessRequestService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping({"/api/admin/technician-requests", "/api/admin/access-requests"})
public class AdminTechnicianRequestController {

    private final TechnicianAccessRequestService requestService;
    private final UserRepository userRepo;

    public AdminTechnicianRequestController(
            TechnicianAccessRequestService requestService,
            UserRepository userRepo) {
        this.requestService = requestService;
        this.userRepo = userRepo;
    }

    public record AccessRequestResponse(
            Long id,
            String fullName,
            String email,
            Long collegeId,
            String institution,
            String department,
            String requestedRole,
            String reason,
            String rejectionReason,
            String status,
            Instant createdAt,
            Instant reviewedAt,
            String reviewedByEmail
    ) {
        public static AccessRequestResponse from(TechnicianAccessRequest request) {
            return new AccessRequestResponse(
                    request.getId(),
                    request.getFullName(),
                    request.getEmail(),
                    request.getCollegeId(),
                    request.getInstitution(),
                    request.getDepartment(),
                    request.getRequestedRole().name(),
                    request.getReason(),
                    request.getRejectionReason(),
                    request.getStatus().name(),
                    request.getCreatedAt(),
                    request.getReviewedAt(),
                    request.getReviewedBy() != null ? request.getReviewedBy().getEmail() : null
            );
        }
    }

    public record RejectBody(String reason) {}

    private User requireAdministrator(String email) {
        if (email == null || email.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication is required");
        }

        User user = userRepo.findByEmail(email)
                .orElseThrow(() ->
                        new org.springframework.web.server
                                .ResponseStatusException(
                                org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "Administrator account not found"));

        if (user.getRole() != User.Role.SYSTEM_ADMINISTRATOR
                || !user.isActive()) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only an active System Administrator can review requests");
        }

        return user;
    }

    @GetMapping("/pending")
    public List<AccessRequestResponse> getPendingRequests(
            @AuthenticationPrincipal String email) {

        requireAdministrator(email);

        return requestService.getPendingRequests()
                .stream()
                .map(AccessRequestResponse::from)
                .toList();
    }

    @GetMapping("/processed")
    public List<AccessRequestResponse> getProcessedRequests(
            @AuthenticationPrincipal String email) {

        requireAdministrator(email);

        return requestService.getProcessedRequests()
                .stream()
                .map(AccessRequestResponse::from)
                .toList();
    }

    @GetMapping("/{requestId}")
    public AccessRequestResponse getRequestDetails(
            @PathVariable Long requestId,
            @AuthenticationPrincipal String email) {

        requireAdministrator(email);

        return AccessRequestResponse.from(requestService.getRequestById(requestId));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long requestId,
            @AuthenticationPrincipal String email) {

        User administrator = requireAdministrator(email);
        requestService.approveRequest(requestId, administrator);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long requestId,
            @RequestBody(required = false) RejectBody body,
            @AuthenticationPrincipal String email) {

        User administrator = requireAdministrator(email);
        String reason = (body != null) ? body.reason() : null;
        requestService.rejectRequest(requestId, administrator, reason);

        return ResponseEntity.noContent().build();
    }
}
