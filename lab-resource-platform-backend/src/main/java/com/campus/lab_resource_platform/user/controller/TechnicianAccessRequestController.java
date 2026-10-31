package com.campus.lab_resource_platform.user.controller;

import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.service.TechnicianAccessRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping({"/api/technician-requests", "/api/access-requests"})
public class TechnicianAccessRequestController {

    private final TechnicianAccessRequestService requestService;

    public TechnicianAccessRequestController(
            TechnicianAccessRequestService requestService) {
        this.requestService = requestService;
    }

    public record SubmitRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(min = 8, max = 72) String password,
            @Size(max = 150) String institution,
            @Size(max = 150) String department,
            String requestedRole,
            @Size(max = 1000) String reason
    ) {}

    public record RequestResponse(
            Long id,
            String fullName,
            String email,
            String institution,
            String department,
            String requestedRole,
            String status,
            String rejectionReason,
            Instant createdAt,
            Instant reviewedAt
    ) {
        public static RequestResponse from(TechnicianAccessRequest request) {
            return new RequestResponse(
                    request.getId(),
                    request.getFullName(),
                    request.getEmail(),
                    request.getInstitution(),
                    request.getDepartment(),
                    request.getRequestedRole().name(),
                    request.getStatus().name(),
                    request.getRejectionReason(),
                    request.getCreatedAt(),
                    request.getReviewedAt()
            );
        }
    }

    @PostMapping
    public ResponseEntity<RequestResponse> submit(
            @Valid @RequestBody SubmitRequest body) {

        User.Role role = User.Role.LAB_TECHNICIAN;
        if (body.requestedRole() != null && !body.requestedRole().isBlank()) {
            try {
                role = User.Role.valueOf(body.requestedRole().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid requested role: " + body.requestedRole());
            }
        }

        TechnicianAccessRequest request =
                requestService.submitRequest(
                        body.fullName(),
                        body.email(),
                        body.password(),
                        body.institution(),
                        body.department(),
                        role,
                        body.reason()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RequestResponse.from(request));
    }

    @GetMapping("/status")
    public List<RequestResponse> checkStatus(
            @RequestParam(required = false) String email,
            @AuthenticationPrincipal String principalEmail) {

        String targetEmail = (email != null && !email.isBlank())
                ? email
                : principalEmail;

        if (targetEmail == null || targetEmail.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email query parameter or authentication is required");
        }

        return requestService.getRequestsByEmail(targetEmail)
                .stream()
                .map(RequestResponse::from)
                .toList();
    }
}
