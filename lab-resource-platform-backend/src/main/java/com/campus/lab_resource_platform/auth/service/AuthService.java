package com.campus.lab_resource_platform.auth.service;

import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.auth.entity.PasswordResetToken;
import com.campus.lab_resource_platform.auth.repository.PasswordResetTokenRepository;
import com.campus.lab_resource_platform.security.JwtService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository tokenRepo;

    public AuthService(
            UserRepository userRepo,
            BCryptPasswordEncoder encoder,
            JwtService jwtService,
            PasswordResetTokenRepository tokenRepo) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.tokenRepo = tokenRepo;
    }

    public User register(RegisterRequest dto) {
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepo.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        User.Role requestedRole;
        try {
            requestedRole = User.Role.valueOf(dto.getRole().trim());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role"
            );
        }

        // Public self-registration only permits RESEARCHER_STUDENT.
        // Privileged roles (ADMIN, MANAGER, TECHNICIAN, etc.) require administrative provisioning or approval workflows.
        if (requestedRole != User.Role.RESEARCHER_STUDENT) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Self-registration is restricted to Researcher / Student accounts. Privileged access requires administrative approval."
            );
        }

        User user = new User();
        user.setFullName(dto.getFullName().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(dto.getPassword()));
        user.setAuthProvider(User.AuthProvider.LOCAL);

        // Public registration must not grant privileged roles.
        user.setRole(User.Role.RESEARCHER_STUDENT);

        user.setEmailVerified(false);
        user.setActive(true);

        User savedUser = userRepo.save(user);

        // The requested role is recognized, but is not granted here.
        // Add a separate role-approval workflow for privileged roles.

        return savedUser;
    }

    public String login(LoginRequest dto) {
        return login(dto, null);
    }

    public String login(LoginRequest dto, HttpServletResponse response) {
        String email = dto.getEmail().trim().toLowerCase();

        User user = userRepo.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "Invalid email or password"));

        if (user.getAuthProvider() != User.AuthProvider.LOCAL
                || user.getPasswordHash() == null
                || !encoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException(
                    "Invalid email or password");
        }

        if (!user.isActive()) {
            throw new DisabledException("Account is inactive");
        }

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getRole().name()
        );

        if (response != null) {
            ResponseCookie cookie = ResponseCookie.from("jwt", token)
                    .httpOnly(true)
                    .secure(false) // Local HTTP development
                    .path("/")
                    .maxAge(Duration.ofHours(1))
                    .sameSite("Lax")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return token;
    }

    public void logout(HttpServletResponse response) {
        if (response != null) {
            ResponseCookie cookie = ResponseCookie.from("jwt", "")
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(0)
                    .sameSite("Lax")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive"
            );
        }

        String rawToken = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

        tokenRepo.save(resetToken);

        return rawToken;
    }

    @Transactional
    public void resetPasswordWithToken(
            String rawToken,
            String newPassword,
            String confirmPassword) {

        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reset token is required"
            );
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match"
            );
        }

        if (newPassword.length() < 8 || newPassword.length() > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 72 characters"
            );
        }

        String tokenHash = hashToken(rawToken.trim());

        PasswordResetToken resetToken = tokenRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired password reset token"
                ));

        if (resetToken.getUsedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password reset token has already been used"
            );
        }

        if (Instant.now().isAfter(resetToken.getExpiresAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password reset token has expired"
            );
        }

        User user = resetToken.getUser();
        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive"
            );
        }

        user.setPasswordHash(encoder.encode(newPassword));
        resetToken.setUsedAt(Instant.now());

        userRepo.save(user);
        tokenRepo.save(resetToken);
    }

    @Transactional
    public void resetPassword(
            String email,
            String newPassword,
            String confirmPassword) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Password reset requires a valid, single-use reset token. Please request a reset token first."
        );
    }
}