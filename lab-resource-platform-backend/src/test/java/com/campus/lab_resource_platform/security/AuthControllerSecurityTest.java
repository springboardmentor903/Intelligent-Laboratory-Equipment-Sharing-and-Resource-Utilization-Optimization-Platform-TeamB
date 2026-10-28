package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.auth.controller.AuthController;
import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.PasswordResetInitRequest;
import com.campus.lab_resource_platform.auth.service.AuthService;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthControllerSecurityTest {

    private AuthService authService;
    private JwtService jwtService;
    private UserRepository userRepository;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);

        authController = new AuthController(authService, userRepository, jwtService);
    }

    @Test
    @DisplayName("login response body contains ONLY generic message and never exposes raw JWT")
    @SuppressWarnings("unchecked")
    void login_responseBodyDoesNotContainJwt() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@campus.edu");
        request.setPassword("Password123!");

        when(authService.login(eq(request), any())).thenReturn("secret.jwt.token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<?> entity = authController.login(request, response);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertTrue(entity.getBody() instanceof Map);
        Map<String, Object> body = (Map<String, Object>) entity.getBody();

        assertEquals("Login successful", body.get("message"));
        assertNull(body.get("token"), "Raw JWT token MUST NOT be returned in the response body");
        assertFalse(body.containsKey("token"), "Response body map must not have a 'token' key");
    }

    @Test
    @DisplayName("requestPasswordReset response body contains generic message and never leaks resetToken")
    void requestPasswordReset_responseBodyDoesNotContainResetToken() {
        PasswordResetInitRequest request = new PasswordResetInitRequest();
        request.setEmail("student@campus.edu");

        when(authService.createPasswordResetToken("student@campus.edu")).thenReturn("generated-raw-token-12345");

        ResponseEntity<Map<String, String>> entity = authController.requestPasswordReset(request);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        Map<String, String> body = entity.getBody();
        assertNotNull(body);

        assertTrue(body.get("message").contains("If an account exists with this email address"));
        assertNull(body.get("resetToken"), "Raw reset token MUST NOT be returned in the public response body");
        assertNull(body.get("token"), "Raw token MUST NOT be returned in the public response body");
        assertFalse(body.containsKey("resetToken"));
        assertFalse(body.containsKey("token"));
    }

    @Test
    @DisplayName("requestPasswordReset for unknown email returns identical generic 200 OK response")
    void requestPasswordReset_unknownEmail_returnsIdenticalGenericResponse() {
        PasswordResetInitRequest request = new PasswordResetInitRequest();
        request.setEmail("nonexistent@campus.edu");

        when(authService.createPasswordResetToken("nonexistent@campus.edu")).thenReturn(null);

        ResponseEntity<Map<String, String>> entity = authController.requestPasswordReset(request);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        Map<String, String> body = entity.getBody();
        assertNotNull(body);

        assertEquals("If an account exists with this email address, password reset instructions have been sent.",
                body.get("message"));
        assertEquals("15", body.get("expiresInMinutes"));
    }

    @Test
    @DisplayName("Password reset public responses for unknown email, successful delivery, and SMTP delivery failure are strictly identical")
    void requestPasswordReset_responsesAreIndistinguishableAcrossAllOutcomes() {
        PasswordResetInitRequest reqUnknown = new PasswordResetInitRequest();
        reqUnknown.setEmail("unknown@campus.edu");
        when(authService.createPasswordResetToken("unknown@campus.edu")).thenReturn(null);

        PasswordResetInitRequest reqSuccess = new PasswordResetInitRequest();
        reqSuccess.setEmail("active@campus.edu");
        when(authService.createPasswordResetToken("active@campus.edu")).thenReturn("valid-raw-token-12345");

        PasswordResetInitRequest reqSmtpFail = new PasswordResetInitRequest();
        reqSmtpFail.setEmail("smtp-error@campus.edu");
        when(authService.createPasswordResetToken("smtp-error@campus.edu")).thenReturn(null);

        ResponseEntity<Map<String, String>> respUnknown = authController.requestPasswordReset(reqUnknown);
        ResponseEntity<Map<String, String>> respSuccess = authController.requestPasswordReset(reqSuccess);
        ResponseEntity<Map<String, String>> respSmtpFail = authController.requestPasswordReset(reqSmtpFail);

        assertEquals(HttpStatus.OK, respUnknown.getStatusCode());
        assertEquals(HttpStatus.OK, respSuccess.getStatusCode());
        assertEquals(HttpStatus.OK, respSmtpFail.getStatusCode());

        assertEquals(respUnknown.getBody(), respSuccess.getBody(),
                "Public response for valid account must be identical to unknown email");
        assertEquals(respUnknown.getBody(), respSmtpFail.getBody(),
                "Public response during SMTP failure must be identical to unknown email");

        assertFalse(respUnknown.getBody().containsKey("token"));
        assertFalse(respSuccess.getBody().containsKey("token"));
        assertFalse(respSmtpFail.getBody().containsKey("token"));
        assertFalse(respUnknown.getBody().containsKey("resetToken"));
        assertFalse(respSuccess.getBody().containsKey("resetToken"));
        assertFalse(respSmtpFail.getBody().containsKey("resetToken"));
    }

    @Test
    @DisplayName("logout invokes authService.logout and returns 200 OK")
    void logout_invokesAuthServiceAndReturnsOk() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<?> entity = authController.logout(response);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        verify(authService).logout(response);
    }
}
