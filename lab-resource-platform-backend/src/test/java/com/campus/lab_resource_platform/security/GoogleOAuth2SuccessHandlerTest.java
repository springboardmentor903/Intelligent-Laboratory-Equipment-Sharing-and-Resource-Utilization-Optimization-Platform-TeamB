package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleOAuth2SuccessHandlerTest {

    private UserRepository userRepository;
    private JwtService jwtService;
    private GoogleOAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        successHandler = new GoogleOAuth2SuccessHandler(userRepository, jwtService);
    }

    @Test
    @DisplayName("onAuthenticationSuccess redirects to unverified_google_account when email is unverified")
    void onAuthenticationSuccess_unverifiedEmail_redirectsWithError() throws Exception {
        OAuth2User oauthUser = mock(OAuth2User.class);
        when(oauthUser.getAttribute("sub")).thenReturn("google-sub-123");
        when(oauthUser.getAttribute("email")).thenReturn("student@gmail.com");
        when(oauthUser.getAttribute("name")).thenReturn("Test Student");
        when(oauthUser.getAttribute("email_verified")).thenReturn(false);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauthUser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, auth);

        assertEquals("http://localhost:5173/oauth/callback?error=unverified_google_account",
                response.getRedirectedUrl());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("onAuthenticationSuccess redirects to account_inactive when existing user is disabled")
    void onAuthenticationSuccess_inactiveUser_redirectsWithError() throws Exception {
        OAuth2User oauthUser = mock(OAuth2User.class);
        when(oauthUser.getAttribute("sub")).thenReturn("google-sub-456");
        when(oauthUser.getAttribute("email")).thenReturn("inactive@campus.edu");
        when(oauthUser.getAttribute("name")).thenReturn("Inactive User");
        when(oauthUser.getAttribute("email_verified")).thenReturn(true);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauthUser);

        User existingUser = new User();
        existingUser.setEmail("inactive@campus.edu");
        existingUser.setActive(false);

        when(userRepository.findByGoogleId("google-sub-456")).thenReturn(Optional.of(existingUser));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, auth);

        assertEquals("http://localhost:5173/oauth/callback?error=account_inactive",
                response.getRedirectedUrl());
        java.util.List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertTrue(setCookies.stream().noneMatch(c -> c.contains("jwt=")),
                "Inactive user must NOT receive a jwt session cookie");
    }

    @Test
    @DisplayName("onAuthenticationSuccess sets HttpOnly jwt cookie and redirects to /oauth/callback for active verified user")
    void onAuthenticationSuccess_activeVerifiedUser_setsCookieAndRedirects() throws Exception {
        OAuth2User oauthUser = mock(OAuth2User.class);
        when(oauthUser.getAttribute("sub")).thenReturn("google-sub-789");
        when(oauthUser.getAttribute("email")).thenReturn("student@campus.edu");
        when(oauthUser.getAttribute("name")).thenReturn("Active Student");
        when(oauthUser.getAttribute("email_verified")).thenReturn(true);
        when(oauthUser.getAttribute("picture")).thenReturn("https://lh3.googleusercontent.com/avatar");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauthUser);

        User user = new User();
        user.setEmail("student@campus.edu");
        user.setActive(true);
        user.setRole(User.Role.RESEARCHER_STUDENT);

        when(userRepository.findByGoogleId("google-sub-789")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("student@campus.edu", "RESEARCHER_STUDENT")).thenReturn("oauth.jwt.token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, auth);

        assertEquals("http://localhost:5173/oauth/callback", response.getRedirectedUrl());

        java.util.List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertFalse(setCookies.isEmpty(), "Response must include Set-Cookie headers");

        String jwtCookie = setCookies.stream()
                .filter(c -> c.contains("jwt="))
                .findFirst()
                .orElse(null);

        assertNotNull(jwtCookie, "jwt cookie must be present in Set-Cookie headers");
        assertTrue(jwtCookie.contains("jwt=oauth.jwt.token"));
        assertTrue(jwtCookie.contains("HttpOnly"));
        assertTrue(jwtCookie.contains("Path=/"));
        assertTrue(jwtCookie.contains("SameSite=Lax"));
        verify(userRepository).save(user);
    }
}
