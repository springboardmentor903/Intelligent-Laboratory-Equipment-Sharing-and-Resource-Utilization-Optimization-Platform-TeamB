package com.campus.lab_resource_platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtFilterTest {

    private JwtService jwtService;
    private JwtFilter jwtFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = mock(JwtService.class);
        jwtFilter = new JwtFilter(jwtService);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Bearer token in Authorization header populates SecurityContext")
    void bearerTokenPopulatesSecurityContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid.token.here")).thenReturn(true);
        when(jwtService.extractEmailByToken("valid.token.here")).thenReturn("student@lab.edu");
        when(jwtService.extractRoleByToken("valid.token.here")).thenReturn("RESEARCHER_STUDENT");

        jwtFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "Authentication should be set in SecurityContext");
        assertEquals("student@lab.edu", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RESEARCHER_STUDENT")));
    }

    @Test
    @DisplayName("JWT in cookie populates SecurityContext when Authorization header is absent")
    void cookieJwtPopulatesSecurityContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", "cookie.jwt.token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("cookie.jwt.token")).thenReturn(true);
        when(jwtService.extractEmailByToken("cookie.jwt.token")).thenReturn("admin@lab.edu");
        when(jwtService.extractRoleByToken("cookie.jwt.token")).thenReturn("SYSTEM_ADMINISTRATOR");

        jwtFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin@lab.edu", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMINISTRATOR")));
    }

    @Test
    @DisplayName("Role prefix is normalized without duplicating ROLE_")
    void rolePrefixNormalizedCorrectly() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token.with.prefix");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("token.with.prefix")).thenReturn(true);
        when(jwtService.extractEmailByToken("token.with.prefix")).thenReturn("tech@lab.edu");
        // Simulated token where role claim already has "ROLE_" prefix
        when(jwtService.extractRoleByToken("token.with.prefix")).thenReturn("ROLE_LAB_TECHNICIAN");

        jwtFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LAB_TECHNICIAN")),
                "Authority must not become ROLE_ROLE_LAB_TECHNICIAN");
    }

    @Test
    @DisplayName("Invalid or missing token leaves SecurityContext unauthenticated")
    void invalidTokenLeavesContextEmpty() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("invalid.token")).thenReturn(false);

        jwtFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must remain unauthenticated for invalid token");
    }

    @Test
    @DisplayName("JWT cookie overrides pre-existing OAuth2 session authentication in SecurityContext")
    void jwtCookieOverridesExistingOAuth2Authentication() throws ServletException, IOException {
        org.springframework.security.oauth2.core.user.OAuth2User oauthUser = mock(org.springframework.security.oauth2.core.user.OAuth2User.class);
        when(oauthUser.getName()).thenReturn("google-sub-999");
        org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken existingOAuth2 =
                new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                        oauthUser,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_openid")),
                        "google"
                );
        SecurityContextHolder.getContext().setAuthentication(existingOAuth2);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", "valid.oauth.jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid.oauth.jwt")).thenReturn(true);
        when(jwtService.extractEmailByToken("valid.oauth.jwt")).thenReturn("student@lab.edu");
        when(jwtService.extractRoleByToken("valid.oauth.jwt")).thenReturn("RESEARCHER_STUDENT");

        jwtFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("student@lab.edu", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RESEARCHER_STUDENT")),
                "Authority must be updated to the user's application role");
    }
}
