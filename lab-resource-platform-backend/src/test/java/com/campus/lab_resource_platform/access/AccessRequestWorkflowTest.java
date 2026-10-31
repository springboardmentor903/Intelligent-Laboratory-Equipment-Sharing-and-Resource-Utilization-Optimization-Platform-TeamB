package com.campus.lab_resource_platform.access;

import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.auth.service.AuthService;
import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.security.JwtService;
import com.campus.lab_resource_platform.user.controller.AdminTechnicianRequestController;
import com.campus.lab_resource_platform.user.controller.TechnicianAccessRequestController;
import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.TechnicianAccessRequestRepository;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.user.service.TechnicianAccessRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AccessRequestWorkflowTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private TechnicianAccessRequestService accessRequestService;

    @Autowired
    private TechnicianAccessRequestController accessRequestController;

    @Autowired
    private AdminTechnicianRequestController adminAccessRequestController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CollegeRepository collegeRepository;

    @Autowired
    private TechnicianAccessRequestRepository requestRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private BCryptPasswordEncoder encoder;

    private User systemAdmin;

    @BeforeEach
    void setup() {
        requestRepository.deleteAll();

        // Ensure system administrator exists
        systemAdmin = userRepository.findByEmail("admin@gmail.com")
                .orElseGet(() -> {
                    User admin = new User();
                    admin.setFullName("System Administrator");
                    admin.setEmail("admin@gmail.com");
                    admin.setPasswordHash(encoder.encode("Admin@123"));
                    admin.setRole(User.Role.SYSTEM_ADMINISTRATOR);
                    admin.setActive(true);
                    admin.setEmailVerified(true);
                    return userRepository.save(admin);
                });
    }

    @Test
    @DisplayName("Public registration cannot request SYSTEM_ADMINISTRATOR")
    void cannotRegisterAsSystemAdministrator() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Hacker");
        req.setEmail("hacker@test.com");
        req.setPassword("Password123!");
        req.setRole("SYSTEM_ADMINISTRATOR");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            authService.register(req);
        });

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("System Administrator"));
    }

    @Test
    @DisplayName("Public registration with privileged role creates pending request and assigns RESEARCHER_STUDENT")
    void registrationWithPrivilegedRoleCreatesPendingRequest() {
        String testEmail = "dean_new@college.edu";
        userRepository.findByEmail(testEmail).ifPresent(userRepository::delete);

        RegisterRequest req = new RegisterRequest();
        req.setFullName("Dr. Dean");
        req.setEmail(testEmail);
        req.setPassword("Password123!");
        req.setRole("INSTITUTION_ADMINISTRATOR");
        req.setInstitution("State University");
        req.setDepartment("Engineering");

        User registeredUser = authService.register(req);

        assertNotNull(registeredUser);
        assertEquals(User.Role.RESEARCHER_STUDENT, registeredUser.getRole());

        // Verify user in database is RESEARCHER_STUDENT
        User userInDb = userRepository.findByEmail(testEmail).orElseThrow();
        assertEquals(User.Role.RESEARCHER_STUDENT, userInDb.getRole());

        // Verify pending request was created
        assertTrue(requestRepository.existsByEmailAndStatus(
                testEmail,
                TechnicianAccessRequest.RequestStatus.PENDING
        ));
    }

    @Test
    @DisplayName("Submit institution onboarding and role access request as PENDING")
    void submitAccessRequestWorkflow() {
        String applicantEmail = "lablead@college.edu";
        userRepository.findByEmail(applicantEmail).ifPresent(userRepository::delete);

        TechnicianAccessRequestController.SubmitRequest submitReq =
                new TechnicianAccessRequestController.SubmitRequest(
                        "Jane Doe",
                        applicantEmail,
                        "Password123!",
                        "MIT Tech Institute",
                        "Robotics",
                        "LAB_MANAGER",
                        "Need manager access for robotics equipment sharing"
                );

        ResponseEntity<TechnicianAccessRequestController.RequestResponse> response =
                accessRequestController.submit(submitReq);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PENDING", response.getBody().status());
        assertEquals("LAB_MANAGER", response.getBody().requestedRole());

        // Query status endpoint
        List<TechnicianAccessRequestController.RequestResponse> statusList =
                accessRequestController.checkStatus(applicantEmail, null);
        assertFalse(statusList.isEmpty());
        assertEquals(applicantEmail, statusList.get(0).email());
        assertEquals("PENDING", statusList.get(0).status());
    }

    @Test
    @DisplayName("Main System Administrator approves request, onboarding college and assigning authorized role")
    void adminApproveRequestWorkflow() {
        String applicantEmail = "depthead@college.edu";
        userRepository.findByEmail(applicantEmail).ifPresent(userRepository::delete);

        // Submit request
        TechnicianAccessRequest request = new TechnicianAccessRequest();
        request.setFullName("Prof. Alan");
        request.setEmail(applicantEmail);
        request.setPasswordHash(encoder.encode("ProfPassword123!"));
        request.setInstitution("Stanford Lab Academy");
        request.setDepartment("Bioengineering");
        request.setRequestedRole(User.Role.DEPARTMENT_HEAD);
        request.setStatus(TechnicianAccessRequest.RequestStatus.PENDING);
        TechnicianAccessRequest savedRequest = requestRepository.save(request);

        // Administrator reviews and approves
        ResponseEntity<Void> response = adminAccessRequestController.approve(
                savedRequest.getId(),
                systemAdmin.getEmail()
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify request updated to APPROVED
        TechnicianAccessRequest approvedRequest = requestRepository.findById(savedRequest.getId()).orElseThrow();
        assertEquals(TechnicianAccessRequest.RequestStatus.APPROVED, approvedRequest.getStatus());
        assertNotNull(approvedRequest.getReviewedAt());
        assertEquals(systemAdmin.getId(), approvedRequest.getReviewedBy().getId());

        // Verify college was created/linked
        Optional<College> college = collegeRepository.findByNameIgnoreCase("Stanford Lab Academy");
        assertTrue(college.isPresent());

        // Verify user account was created with approved role and college association
        User approvedUser = userRepository.findByEmail(applicantEmail).orElseThrow();
        assertEquals(User.Role.DEPARTMENT_HEAD, approvedUser.getRole());
        assertEquals(college.get().getId(), approvedUser.getCollegeId());
        assertTrue(encoder.matches("ProfPassword123!", approvedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("Main System Administrator rejects request, recording rejection reason without granting role")
    void adminRejectRequestWorkflow() {
        String applicantEmail = "rejected_applicant@college.edu";
        userRepository.findByEmail(applicantEmail).ifPresent(userRepository::delete);

        TechnicianAccessRequest request = new TechnicianAccessRequest();
        request.setFullName("John Reject");
        request.setEmail(applicantEmail);
        request.setInstitution("Unknown Academy");
        request.setRequestedRole(User.Role.LAB_TECHNICIAN);
        request.setStatus(TechnicianAccessRequest.RequestStatus.PENDING);
        TechnicianAccessRequest savedRequest = requestRepository.save(request);

        AdminTechnicianRequestController.RejectBody rejectBody =
                new AdminTechnicianRequestController.RejectBody("Institutional verification failed");

        ResponseEntity<Void> response = adminAccessRequestController.reject(
                savedRequest.getId(),
                rejectBody,
                systemAdmin.getEmail()
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        TechnicianAccessRequest rejectedRequest = requestRepository.findById(savedRequest.getId()).orElseThrow();
        assertEquals(TechnicianAccessRequest.RequestStatus.REJECTED, rejectedRequest.getStatus());
        assertEquals("Institutional verification failed", rejectedRequest.getRejectionReason());

        // Verify user account was NOT granted technician role
        assertTrue(userRepository.findByEmail(applicantEmail).isEmpty());
    }

    @Test
    @DisplayName("Non-administrator cannot review access requests")
    void nonAdminCannotApprove() {
        // Standard researcher user
        User student = userRepository.findByEmail("student_tester@gmail.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFullName("Student Tester");
                    u.setEmail("student_tester@gmail.com");
                    u.setRole(User.Role.RESEARCHER_STUDENT);
                    u.setActive(true);
                    return userRepository.save(u);
                });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            adminAccessRequestController.getPendingRequests(student.getEmail());
        });

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("System administrator cannot approve their own access request")
    void adminCannotApproveSelfRequest() {
        TechnicianAccessRequest request = new TechnicianAccessRequest();
        request.setFullName("Admin Self");
        request.setEmail(systemAdmin.getEmail());
        request.setRequestedRole(User.Role.INSTITUTION_ADMINISTRATOR);
        request.setStatus(TechnicianAccessRequest.RequestStatus.PENDING);
        final TechnicianAccessRequest savedRequest = requestRepository.save(request);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            accessRequestService.approveRequest(savedRequest.getId(), systemAdmin);
        });

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cannot approve their own"));
    }
}
