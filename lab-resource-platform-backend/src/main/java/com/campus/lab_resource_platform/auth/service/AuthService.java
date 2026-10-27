package com.campus.lab_resource_platform.auth.service;

import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
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

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepo,
            BCryptPasswordEncoder encoder,
            JwtService jwtService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest dto) {
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepo.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        try {
            User.Role.valueOf(dto.getRole().trim());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role"
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

    @Transactional
    public void resetPassword(
            String email,
            String newPassword,
            String confirmPassword) {

        if (!newPassword.equals(confirmPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match"
            );
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

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

        user.setPasswordHash(
                encoder.encode(newPassword)
        );

        userRepo.save(user);
    }
}