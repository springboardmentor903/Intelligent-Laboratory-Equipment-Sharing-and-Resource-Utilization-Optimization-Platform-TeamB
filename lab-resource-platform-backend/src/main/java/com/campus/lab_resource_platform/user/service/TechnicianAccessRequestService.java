package com.campus.lab_resource_platform.user.service;

import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.TechnicianAccessRequestRepository;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TechnicianAccessRequestService {

    private static final Set<User.Role> PERMITTED_REQUEST_ROLES = Set.of(
            User.Role.SYSTEM_ADMINISTRATOR,
            User.Role.INSTITUTION_ADMINISTRATOR,
            User.Role.DEPARTMENT_HEAD,
            User.Role.LAB_MANAGER,
            User.Role.LAB_TECHNICIAN
    );

    private final TechnicianAccessRequestRepository requestRepo;
    private final UserRepository userRepo;
    private final CollegeRepository collegeRepo;
    private final BCryptPasswordEncoder encoder;

    public TechnicianAccessRequestService(
            TechnicianAccessRequestRepository requestRepo,
            UserRepository userRepo,
            CollegeRepository collegeRepo,
            BCryptPasswordEncoder encoder) {
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
        this.collegeRepo = collegeRepo;
        this.encoder = encoder;
    }

    @Transactional
    public TechnicianAccessRequest submitRequest(
            String fullName,
            String email,
            String rawPassword,
            String institution,
            String department,
            User.Role requestedRole,
            String reason) {

        if (fullName == null || fullName.isBlank()
                || email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name and email are required");
        }

        final User.Role targetRole = (requestedRole == null)
                ? User.Role.LAB_TECHNICIAN
                : requestedRole;

        if (!PERMITTED_REQUEST_ROLES.contains(targetRole)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role " + targetRole + " cannot be requested via access requests");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // Check if user already has an active account with this exact privileged role
        userRepo.findByEmail(normalizedEmail).ifPresent(existingUser -> {
            if (existingUser.getRole() == targetRole && existingUser.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An active account with this role already exists for this email");
            }
        });

        if (requestRepo.existsByEmailAndStatus(
                normalizedEmail,
                TechnicianAccessRequest.RequestStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A request for this email is already pending");
        }

        TechnicianAccessRequest request = new TechnicianAccessRequest();
        request.setFullName(fullName.trim());
        request.setEmail(normalizedEmail);
        if (rawPassword != null && !rawPassword.isBlank()) {
            request.setPasswordHash(encoder.encode(rawPassword.trim()));
        }
        request.setInstitution(
                institution == null || institution.isBlank() ? null : institution.trim());
        request.setDepartment(
                department == null || department.isBlank() ? null : department.trim());
        request.setRequestedRole(targetRole);
        request.setReason(
                reason == null || reason.isBlank() ? null : reason.trim());
        request.setStatus(TechnicianAccessRequest.RequestStatus.PENDING);

        return requestRepo.save(request);
    }

    @Transactional(readOnly = true)
    public List<TechnicianAccessRequest> getPendingRequests() {
        return requestRepo.findByStatusOrderByCreatedAtAsc(
                TechnicianAccessRequest.RequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<TechnicianAccessRequest> getProcessedRequests() {
        return requestRepo.findByStatusInOrderByCreatedAtDesc(
                List.of(
                        TechnicianAccessRequest.RequestStatus.APPROVED,
                        TechnicianAccessRequest.RequestStatus.REJECTED
                )
        );
    }

    @Transactional(readOnly = true)
    public TechnicianAccessRequest getRequestById(Long requestId) {
        return requestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Access request not found"));
    }

    @Transactional(readOnly = true)
    public List<TechnicianAccessRequest> getRequestsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return requestRepo.findByEmailIgnoreCaseOrderByCreatedAtDesc(email.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional
    public void approveRequest(Long requestId, User administrator) {
        TechnicianAccessRequest request = findPendingRequest(requestId);

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (administrator.getEmail() != null && administrator.getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrators cannot approve their own access requests");
        }

        // Onboard or associate college/institution if provided
        Long collegeId = null;
        if (request.getInstitution() != null && !request.getInstitution().isBlank()) {
            String collegeName = request.getInstitution().trim();
            College college = collegeRepo.findByNameIgnoreCase(collegeName)
                    .orElseGet(() -> {
                        College newCollege = new College();
                        newCollege.setName(collegeName);
                        newCollege.setCode(generateUniqueCollegeCode(collegeName));
                        return collegeRepo.save(newCollege);
                    });
            collegeId = college.getId();
            request.setCollegeId(collegeId);
        }

        // If user already exists in the system (e.g. registered or Google user), update their role, password, and institution
        User user = userRepo.findByEmail(email).orElse(null);

        if (user != null) {
            user.setRole(request.getRequestedRole());
            if (request.getPasswordHash() != null) {
                user.setPasswordHash(request.getPasswordHash());
            }
            if (collegeId != null) {
                user.setCollegeId(collegeId);
                user.setInstitutionId(collegeId);
            }
            user.setActive(true);
            userRepo.save(user);
        } else {
            // Create user account with approved role and college association
            User newUser = new User();
            newUser.setFullName(request.getFullName());
            newUser.setEmail(email);
            newUser.setAuthProvider(User.AuthProvider.LOCAL);
            newUser.setRole(request.getRequestedRole());
            newUser.setEmailVerified(true);
            newUser.setActive(true);
            newUser.setPasswordHash(request.getPasswordHash());
            if (collegeId != null) {
                newUser.setCollegeId(collegeId);
                newUser.setInstitutionId(collegeId);
            }
            userRepo.save(newUser);
        }

        request.setStatus(TechnicianAccessRequest.RequestStatus.APPROVED);
        request.setReviewedBy(administrator);
        request.setReviewedAt(Instant.now());

        requestRepo.save(request);
    }

    @Transactional
    public void rejectRequest(Long requestId, User administrator, String rejectionReason) {
        TechnicianAccessRequest request = findPendingRequest(requestId);

        request.setStatus(TechnicianAccessRequest.RequestStatus.REJECTED);
        request.setReviewedBy(administrator);
        request.setReviewedAt(Instant.now());
        if (rejectionReason != null && !rejectionReason.isBlank()) {
            request.setRejectionReason(rejectionReason.trim());
        }

        requestRepo.save(request);
    }

    private TechnicianAccessRequest findPendingRequest(Long requestId) {
        TechnicianAccessRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Access request not found"));

        if (request.getStatus() != TechnicianAccessRequest.RequestStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request has already been reviewed");
        }

        return request;
    }

    private String generateUniqueCollegeCode(String name) {
        String base = name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.length() > 6) {
            base = base.substring(0, 6);
        }
        if (base.isBlank()) {
            base = "COL";
        }

        String code = base;
        int counter = 1;
        while (collegeRepo.existsByCodeIgnoreCase(code)) {
            code = base + counter++;
        }
        return code;
    }
}
