package com.campus.lab_resource_platform.user.controller;

import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminUserController(
            UserRepository userRepository,
            CollegeRepository collegeRepository,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record AdminUserResponse(
            Long id,
            String fullName,
            String email,
            String role,
            Long collegeId,
            String collegeName,
            Long departmentId,
            String phone,
            boolean active,
            boolean emailVerified,
            String authProvider,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        public static AdminUserResponse from(User user, Map<Long, College> collegeMap) {
            String collegeName = null;
            if (user.getCollegeId() != null && collegeMap.containsKey(user.getCollegeId())) {
                collegeName = collegeMap.get(user.getCollegeId()).getName();
            } else if (user.getInstitutionId() != null && collegeMap.containsKey(user.getInstitutionId())) {
                collegeName = collegeMap.get(user.getInstitutionId()).getName();
            }

            return new AdminUserResponse(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getRole() != null ? user.getRole().name() : null,
                    user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId(),
                    collegeName,
                    user.getDepartmentId(),
                    user.getPhone(),
                    user.isActive(),
                    user.isEmailVerified(),
                    user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL",
                    user.getCreatedAt(),
                    user.getLastLoginAt()
            );
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank String role,
            Long collegeId,
            Long departmentId,
            String phone
    ) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            String password,
            @NotBlank String role,
            Long collegeId,
            Long departmentId,
            String phone,
            Boolean active
    ) {}

    private User requireAdministrator(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin account not found"));
        if (user.getRole() != User.Role.SYSTEM_ADMINISTRATOR || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only System Administrators can manage user accounts");
        }
        return user;
    }

    @GetMapping
    public List<AdminUserResponse> getAllUsers(
            @RequestParam(required = false) Long collegeId,
            @AuthenticationPrincipal String adminEmail) {
        requireAdministrator(adminEmail);

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        List<User> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return users.stream()
                .filter(u -> {
                    if (collegeId == null) return true;
                    Long userCol = u.getCollegeId() != null ? u.getCollegeId() : u.getInstitutionId();
                    return collegeId.equals(userCol);
                })
                .map(u -> AdminUserResponse.from(u, collegeMap))
                .toList();
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> createUser(
            @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal String adminEmail) {
        requireAdministrator(adminEmail);

        String email = req.email().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
        }

        User.Role role;
        try {
            role = User.Role.valueOf(req.role());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + req.role());
        }

        User newUser = new User();
        newUser.setFullName(req.fullName().trim());
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(req.password().trim()));
        newUser.setRole(role);
        newUser.setCollegeId(req.collegeId());
        newUser.setInstitutionId(req.collegeId());
        newUser.setDepartmentId(req.departmentId());
        newUser.setPhone(req.phone() != null ? req.phone().trim() : null);
        newUser.setActive(true);
        newUser.setEmailVerified(true);
        newUser.setAuthProvider(User.AuthProvider.LOCAL);

        User saved = userRepository.save(newUser);

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        return ResponseEntity.status(HttpStatus.CREATED).body(AdminUserResponse.from(saved, collegeMap));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest req,
            @AuthenticationPrincipal String adminEmail) {
        requireAdministrator(adminEmail);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String email = req.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already taken by another user");
        }

        User.Role role;
        try {
            role = User.Role.valueOf(req.role());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + req.role());
        }

        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setRole(role);
        user.setCollegeId(req.collegeId());
        user.setInstitutionId(req.collegeId());
        user.setDepartmentId(req.departmentId());
        user.setPhone(req.phone() != null ? req.phone().trim() : null);
        if (req.active() != null) {
            user.setActive(req.active());
        }
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password().trim()));
        }

        User updated = userRepository.save(user);

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        return ResponseEntity.ok(AdminUserResponse.from(updated, collegeMap));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal String adminEmail) {
        User admin = requireAdministrator(adminEmail);

        if (admin.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own administrator account");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> toggleUserStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal String adminEmail) {
        User admin = requireAdministrator(adminEmail);

        if (admin.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own administrator account");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setActive(!user.isActive());
        User saved = userRepository.save(user);

        Map<Long, College> collegeMap = collegeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        return ResponseEntity.ok(AdminUserResponse.from(saved, collegeMap));
    }
}

