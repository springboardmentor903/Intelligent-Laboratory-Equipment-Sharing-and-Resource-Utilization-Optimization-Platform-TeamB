
package com.campus.lab_resource_platform.user.service;

import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.TechnicianAccessRequestRepository;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class TechnicianAccessRequestService {

    private final TechnicianAccessRequestRepository requestRepo;
    private final UserRepository userRepo;

    public TechnicianAccessRequestService(
            TechnicianAccessRequestRepository requestRepo,
            UserRepository userRepo) {
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public TechnicianAccessRequest submitRequest(
            String fullName,
            String email,
            String institution,
            String reason) {

        if (fullName == null || fullName.isBlank()
                || email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name and email are required");
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        if (userRepo.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }

        if (requestRepo.existsByEmailAndStatus(
                normalizedEmail,
                TechnicianAccessRequest.RequestStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A request for this email is already pending");
        }

        TechnicianAccessRequest request =
                new TechnicianAccessRequest();

        request.setFullName(fullName.trim());
        request.setEmail(normalizedEmail);
        request.setInstitution(
                institution == null || institution.isBlank()
                        ? null : institution.trim());
        request.setReason(
                reason == null || reason.isBlank()
                        ? null : reason.trim());
        request.setStatus(
                TechnicianAccessRequest.RequestStatus.PENDING);

        return requestRepo.save(request);
    }

    @Transactional(readOnly = true)
    public List<TechnicianAccessRequest> getPendingRequests() {
        return requestRepo.findByStatusOrderByCreatedAtAsc(
                TechnicianAccessRequest.RequestStatus.PENDING);
    }

    @Transactional
    public void approveRequest(Long requestId, User administrator) {
        TechnicianAccessRequest request = findPendingRequest(requestId);

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepo.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }

        // Create the account only after administrator approval.
        // It remains inactive until the applicant completes
        // the secure password-setup process.
        User technician = new User();
        technician.setFullName(request.getFullName());
        technician.setEmail(email);
        technician.setAuthProvider(User.AuthProvider.LOCAL);
        technician.setRole(User.Role.LAB_TECHNICIAN);
        technician.setEmailVerified(false);
        technician.setActive(false);
        technician.setPasswordHash(null);

        userRepo.save(technician);

        request.setStatus(
                TechnicianAccessRequest.RequestStatus.APPROVED);
        request.setReviewedBy(administrator);
        request.setReviewedAt(Instant.now());

        requestRepo.save(request);
    }

    @Transactional
    public void rejectRequest(Long requestId, User administrator) {
        TechnicianAccessRequest request = findPendingRequest(requestId);

        request.setStatus(
                TechnicianAccessRequest.RequestStatus.REJECTED);
        request.setReviewedBy(administrator);
        request.setReviewedAt(Instant.now());

        requestRepo.save(request);
    }

    private TechnicianAccessRequest findPendingRequest(Long requestId) {
        TechnicianAccessRequest request =
                requestRepo.findById(requestId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Technician request not found"));

        if (request.getStatus()
                != TechnicianAccessRequest.RequestStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request has already been reviewed");
        }

        return request;
    }
}
