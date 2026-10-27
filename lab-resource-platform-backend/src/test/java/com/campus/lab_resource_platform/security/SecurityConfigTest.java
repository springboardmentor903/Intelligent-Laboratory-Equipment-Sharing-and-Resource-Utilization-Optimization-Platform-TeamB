package com.campus.lab_resource_platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private final RequestMatcher apiRequestMatcher = SecurityConfig.createApiRequestMatcher();
    private final SecurityConfig securityConfig = new SecurityConfig(null, null);

    @Test
    @DisplayName("API request matcher matches /api/** and /auth/** endpoints")
    void apiRequestMatcherMatchesApiEndpoints() {
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/users/me");
        assertTrue(apiRequestMatcher.matches(request1), "/api/users/me must be recognized as an API request");

        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/auth/mylogin");
        assertTrue(apiRequestMatcher.matches(request2), "/api/auth/mylogin must be recognized as an API request");

        MockHttpServletRequest request3 = new MockHttpServletRequest("POST", "/auth/forgot-password");
        assertTrue(apiRequestMatcher.matches(request3), "/auth/forgot-password must be recognized as an API request");
    }

    @Test
    @DisplayName("API request matcher handles configured servlet context path correctly")
    void apiRequestMatcherHandlesContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/users/me");
        request.setContextPath("/app");
        assertTrue(apiRequestMatcher.matches(request), "Request with context path /app must still match /api/users/me");

        MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/app/auth/login");
        authRequest.setContextPath("/app");
        assertTrue(apiRequestMatcher.matches(authRequest), "Request with context path /app must still match /auth/login");
    }

    @Test
    @DisplayName("API request matcher does NOT match OAuth endpoints, /error, or browser HTML navigations")
    void apiRequestMatcherDoesNotMatchOAuthOrErrorEndpoints() {
        MockHttpServletRequest oauthInit = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        assertFalse(apiRequestMatcher.matches(oauthInit), "OAuth2 authorization URL must NOT be classified as an API request");

        MockHttpServletRequest oauthCode = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        assertFalse(apiRequestMatcher.matches(oauthCode), "OAuth2 code callback must NOT be classified as an API request");

        MockHttpServletRequest errorRequest = new MockHttpServletRequest("GET", "/error");
        assertFalse(apiRequestMatcher.matches(errorRequest), "/error must NOT be classified as an API request to prevent redirect loops");

        MockHttpServletRequest htmlRequest = new MockHttpServletRequest("GET", "/dashboard");
        htmlRequest.addHeader("Accept", "text/html,application/xhtml+xml");
        assertFalse(apiRequestMatcher.matches(htmlRequest), "Standard HTML browser request must NOT be classified as an API request");
    }

    @Test
    @DisplayName("API request matcher matches AJAX or JSON accept requests on arbitrary paths")
    void apiRequestMatcherMatchesAjaxOrJsonRequests() {
        MockHttpServletRequest ajaxRequest = new MockHttpServletRequest("GET", "/some-resource");
        ajaxRequest.addHeader("X-Requested-With", "XMLHttpRequest");
        assertTrue(apiRequestMatcher.matches(ajaxRequest), "Request with X-Requested-With: XMLHttpRequest must match");

        MockHttpServletRequest jsonRequest = new MockHttpServletRequest("GET", "/other-resource");
        jsonRequest.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        assertTrue(apiRequestMatcher.matches(jsonRequest), "Request with Accept: application/json must match");
    }

    @Test
    @DisplayName("CORS configuration allows both port 5173 and 5174 with credentials")
    void corsConfigurationAllowsFrontendPortsWithCredentials() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source, "CORS configuration source must not be null");

        MockHttpServletRequest request5173 = new MockHttpServletRequest("GET", "/api/users/me");
        CorsConfiguration config5173 = source.getCorsConfiguration(request5173);
        assertNotNull(config5173, "CorsConfiguration must be registered for /**");

        assertEquals("http://localhost:5173", config5173.checkOrigin("http://localhost:5173"),
                "Origin http://localhost:5173 must be allowed");
        assertEquals("http://localhost:5174", config5173.checkOrigin("http://localhost:5174"),
                "Origin http://localhost:5174 must be allowed");
        assertEquals("http://localhost:5175", config5173.checkOrigin("http://localhost:5175"),
                "Arbitrary localhost port should be allowed via origin patterns");

        assertTrue(Boolean.TRUE.equals(config5173.getAllowCredentials()),
                "allowCredentials must be true for session/cookie auth");

        assertTrue(config5173.getAllowedMethods().contains("GET"));
        assertTrue(config5173.getAllowedMethods().contains("POST"));
        assertTrue(config5173.getAllowedMethods().contains("OPTIONS"));
        assertTrue(config5173.getAllowedMethods().contains("DELETE"));

        assertTrue(config5173.getExposedHeaders().contains("Set-Cookie"));
        assertTrue(config5173.getExposedHeaders().contains("Authorization"));
    }
}
