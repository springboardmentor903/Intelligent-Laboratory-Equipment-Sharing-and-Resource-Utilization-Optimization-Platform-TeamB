package com.campus.lab_resource_platform.auth.controller;

import com.campus.lab_resource_platform.auth.dto.ForgotPasswordRequest;
import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.PasswordResetConfirmRequest;
import com.campus.lab_resource_platform.auth.dto.PasswordResetInitRequest;
import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.auth.service.AuthService;
import com.campus.lab_resource_platform.security.JwtService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            UserRepository userRepo,
            JwtService jwtService) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.jwtService = jwtService;
    }

    @PostMapping({"/register", "/myregister"})
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest dto) {

        User user = authService.register(dto);

        // Never return the complete User entity or password hash.
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", "Registration successful",
                        "id", user.getId(),
                        "fullName", user.getFullName(),
                        "email", user.getEmail(),
                        "role", user.getRole().name()
                )
        );
    }

    @PostMapping({"/login", "/mylogin"})
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest dto,
            HttpServletResponse response) {

        try {
            authService.login(dto, response);

            return ResponseEntity.ok(
                    Map.of("message", "Login successful")
            );

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Invalid email or password")
            );

        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "Account is inactive")
            );
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        authService.logout(response);

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully")
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            HttpServletRequest request) {

        String token = extractToken(request);

        if (token == null || !jwtService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Not authenticated")
            );
        }

        String email = jwtService.extractEmailByToken(token);

        User user = userRepo.findByEmail(email).orElse(null);

        if (user == null || !user.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Not authenticated")
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "role", user.getRole().name(),
                        "profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl()
                )
        );
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetInitRequest request) {

        authService.createPasswordResetToken(request.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "If an account exists with this email address, password reset instructions have been sent.",
                "expiresInMinutes", "15"
        ));
    }

    @PostMapping({"/forgot-password/confirm", "/reset-password"})
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {

        authService.resetPasswordWithToken(
                request.getToken(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Password has been reset successfully. Please sign in with your new password."
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {

        if (request.getToken() != null && !request.getToken().trim().isEmpty()) {
            authService.resetPasswordWithToken(
                    request.getToken(),
                    request.getNewPassword(),
                    request.getConfirmPassword()
            );
            return ResponseEntity.ok(Map.of(
                    "message", "Password has been reset successfully. Please sign in with your new password."
            ));
        }

        // If only email is provided, generate a reset token
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty() &&
                (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty())) {
            authService.createPasswordResetToken(request.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists with this email address, password reset instructions have been sent.",
                    "expiresInMinutes", "15"
            ));
        }

        // If email and passwords were provided without a token, reject the insecure attempt
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Password reset requires a valid, single-use reset token. Please request a reset token first."
        );
    }
}