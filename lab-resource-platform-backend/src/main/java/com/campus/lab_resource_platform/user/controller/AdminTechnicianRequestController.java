
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
@RequestMapping("/api/admin/technician-requests")
public class AdminTechnicianRequestController {

    private final TechnicianAccessRequestService requestService;
    private final UserRepository userRepo;

    public AdminTechnicianRequestController(
            TechnicianAccessRequestService requestService,
            UserRepository userRepo) {
        this.requestService = requestService;
        this.userRepo = userRepo;
    }

    public record PendingRequestResponse(
            Long id,
            String fullName,
            String email,
            String institution,
            String reason,
            String status,
            Instant createdAt
    ) {
        static PendingRequestResponse from(
                TechnicianAccessRequest request) {
            return new PendingRequestResponse(
                    request.getId(),
                    request.getFullName(),
                    request.getEmail(),
                    request.getInstitution(),
                    request.getReason(),
                    request.getStatus().name(),
                    request.getCreatedAt()
            );
        }
    }

    private User requireAdministrator(String email) {
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
    public List<PendingRequestResponse> getPendingRequests(
            @AuthenticationPrincipal String email) {

        requireAdministrator(email);

        return requestService.getPendingRequests()
                .stream()
                .map(PendingRequestResponse::from)
                .toList();
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
            @AuthenticationPrincipal String email) {

        User administrator = requireAdministrator(email);
        requestService.rejectRequest(requestId, administrator);

        return ResponseEntity.noContent().build();
    }
}
