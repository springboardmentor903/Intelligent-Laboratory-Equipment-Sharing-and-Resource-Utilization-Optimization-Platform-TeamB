package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.auth.entity.PasswordResetToken;
import com.campus.lab_resource_platform.auth.repository.PasswordResetTokenRepository;
import com.campus.lab_resource_platform.auth.service.AuthService;
import com.campus.lab_resource_platform.auth.service.EmailService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
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
    private EmailService emailService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        jwtService = mock(JwtService.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        emailService = mock(EmailService.class);

        authService = new AuthService(userRepository, encoder, jwtService, tokenRepository, emailService);
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
        assertNotEquals(rawToken, savedToken.getTokenHash(), "Raw token must never be stored directly in the database");
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));
        assertNull(savedToken.getUsedAt());

        verify(emailService).sendPasswordResetEmail("user@campus.edu", rawToken);
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

    @Test
    void createPasswordResetToken_unknownEmail_returnsNullSilently() {
        when(userRepository.findByEmail("unknown@campus.edu")).thenReturn(Optional.empty());

        String token = authService.createPasswordResetToken("unknown@campus.edu");

        assertNull(token, "Must return null silently without throwing exception to prevent account enumeration");
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void register_rejectsTechnicianRoleEscalation_withForbiddenStatus() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Malicious Tech");
        request.setEmail("tech@campus.edu");
        request.setPassword("Password123!");
        request.setRole("LAB_TECHNICIAN");

        when(userRepository.existsByEmail("tech@campus.edu")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsDepartmentHeadRoleEscalation_withForbiddenStatus() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Malicious Head");
        request.setEmail("head@campus.edu");
        request.setPassword("Password123!");
        request.setRole("DEPARTMENT_HEAD");

        when(userRepository.existsByEmail("head@campus.edu")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsInstitutionAdminRoleEscalation_withForbiddenStatus() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Malicious InstAdmin");
        request.setEmail("instadmin@campus.edu");
        request.setPassword("Password123!");
        request.setRole("INSTITUTION_ADMINISTRATOR");

        when(userRepository.existsByEmail("instadmin@campus.edu")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordWithToken_passwordsMismatch_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPasswordWithToken("some-token", "NewPassword123", "MismatchPassword456")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Passwords do not match"));
    }

    @Test
    void resetPasswordWithToken_passwordTooShort_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPasswordWithToken("some-token", "short", "short")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("between 8 and 72 characters"));
    }

    @Test
    void createPasswordResetToken_inactiveUser_returnsNull_andNeverSendsEmail() {
        User inactiveUser = new User();
        inactiveUser.setEmail("inactive@campus.edu");
        inactiveUser.setActive(false);

        when(userRepository.findByEmail("inactive@campus.edu")).thenReturn(Optional.of(inactiveUser));

        String token = authService.createPasswordResetToken("inactive@campus.edu");

        assertNull(token, "Inactive accounts must return null silently to avoid enumeration");
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void createPasswordResetToken_emailDeliveryFailure_invalidatesToken_andReturnsNull() {
        User user = new User();
        user.setEmail("user@campus.edu");
        user.setActive(true);

        when(userRepository.findByEmail("user@campus.edu")).thenReturn(Optional.of(user));
        doThrow(new org.springframework.mail.MailSendException("SMTP delivery failed"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString());

        String result = authService.createPasswordResetToken("user@campus.edu");

        assertNull(result, "createPasswordResetToken must return null on delivery failure to prevent token exposure");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());

        java.util.List<PasswordResetToken> savedTokens = tokenCaptor.getAllValues();
        PasswordResetToken invalidatedToken = savedTokens.get(1);
        assertNotNull(invalidatedToken.getUsedAt(), "Reset token must be invalidated (usedAt set) on email delivery failure");
    }

    @Test
    void resetPasswordWithToken_rejectsTokenInvalidatedByDeliveryFailure() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUsedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPasswordWithToken("invalidated-token", "NewPassword123!", "NewPassword123!")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already been used"));
    }

    @Test
    void login_issuesHttpOnlyJwtCookie_withStrictAttributes() {
        User user = new User();
        user.setEmail("valid@campus.edu");
        user.setPasswordHash(encoder.encode("ValidPassword123"));
        user.setActive(true);
        user.setRole(User.Role.RESEARCHER_STUDENT);
        user.setAuthProvider(User.AuthProvider.LOCAL);

        when(userRepository.findByEmail("valid@campus.edu")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("valid@campus.edu", "RESEARCHER_STUDENT")).thenReturn("mocked.jwt.token");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("valid@campus.edu");
        loginRequest.setPassword("ValidPassword123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        String returnedToken = authService.login(loginRequest, response);

        assertEquals("mocked.jwt.token", returnedToken);
        String cookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookieHeader, "Login must set a Set-Cookie header");
        assertTrue(cookieHeader.contains("jwt=mocked.jwt.token"));
        assertTrue(cookieHeader.contains("HttpOnly"));
        assertTrue(cookieHeader.contains("SameSite=Lax"));
        assertTrue(cookieHeader.contains("Path=/"));
        assertTrue(cookieHeader.contains("Max-Age=3600"));
    }

    @Test
    void logout_clearsHttpOnlyJwtCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        authService.logout(response);

        String cookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookieHeader, "Logout must set a Set-Cookie header");
        assertTrue(cookieHeader.contains("jwt="));
        assertTrue(cookieHeader.contains("HttpOnly"));
        assertTrue(cookieHeader.contains("Max-Age=0"));
    }
}
