package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.auth.entity.PasswordResetToken;
import com.campus.lab_resource_platform.auth.repository.PasswordResetTokenRepository;
import com.campus.lab_resource_platform.auth.service.AuthService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceSecurityTest {

    private UserRepository userRepository;
    private BCryptPasswordEncoder encoder;
    private JwtService jwtService;
    private PasswordResetTokenRepository tokenRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        jwtService = mock(JwtService.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);

        authService = new AuthService(userRepository, encoder, jwtService, tokenRepository);
    }

    @Test
    void register_rejectsPrivilegedRoleEscalation_withForbiddenStatus() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Malicious User");
        request.setEmail("attacker@campus.edu");
        request.setPassword("Password123!");
        request.setRole("SYSTEM_ADMINISTRATOR");

        when(userRepository.existsByEmail("attacker@campus.edu")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("restricted to Researcher / Student"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsLabManagerRoleEscalation_withForbiddenStatus() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Unapproved Manager");
        request.setEmail("manager@campus.edu");
        request.setPassword("Password123!");
        request.setRole("LAB_MANAGER");

        when(userRepository.existsByEmail("manager@campus.edu")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_allowsStudentResearcherRole_andPersistsUser() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Valid Student");
        request.setEmail("student@campus.edu");
        request.setPassword("SecurePass123");
        request.setRole("RESEARCHER_STUDENT");

        when(userRepository.existsByEmail("student@campus.edu")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = authService.register(request);

        assertNotNull(registered);
        assertEquals(User.Role.RESEARCHER_STUDENT, registered.getRole());
        assertEquals("student@campus.edu", registered.getEmail());
        assertTrue(encoder.matches("SecurePass123", registered.getPasswordHash()));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void resetPassword_withoutToken_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPassword("victim@campus.edu", "NewPass123", "NewPass123")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("single-use reset token"));
    }

    @Test
    void createPasswordResetToken_generatesExpiringToken() {
        User user = new User();
        user.setEmail("user@campus.edu");
        user.setActive(true);

        when(userRepository.findByEmail("user@campus.edu")).thenReturn(Optional.of(user));

        String rawToken = authService.createPasswordResetToken("user@campus.edu");

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getTokenHash());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));
        assertNull(savedToken.getUsedAt());
    }

    @Test
    void resetPasswordWithToken_updatesPassword_andMarksTokenUsed() {
        User user = new User();
        user.setEmail("user@campus.edu");
        user.setActive(true);
        user.setPasswordHash(encoder.encode("OldPassword123"));

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        token.setUsedAt(null);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        authService.resetPasswordWithToken("some-valid-raw-token", "BrandNewPass123", "BrandNewPass123");

        assertTrue(encoder.matches("BrandNewPass123", user.getPasswordHash()));
        assertNotNull(token.getUsedAt());
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPasswordWithToken_rejectsAlreadyUsedToken() {
        User user = new User();
        user.setEmail("user@campus.edu");
        user.setActive(true);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        token.setUsedAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPasswordWithToken("used-token", "NewPass123!", "NewPass123!")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already been used"));
    }

    @Test
    void resetPasswordWithToken_rejectsExpiredToken() {
        User user = new User();
        user.setEmail("user@campus.edu");
        user.setActive(true);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        token.setUsedAt(null);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPasswordWithToken("expired-token", "NewPass123!", "NewPass123!")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
    }
}
