package com.campus.lab_resource_platform.user.controller;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerTest {

    private UserRepository userRepository;
    private UserController userController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userController = new UserController(userRepository);
    }

    @Test
    @DisplayName("getCurrentUser returns user profile for UsernamePasswordAuthenticationToken")
    void getCurrentUser_usernamePasswordAuth_returnsProfile() {
        User user = new User();
        user.setId(10L);
        user.setEmail("researcher@campus.edu");
        user.setFullName("Dr. Researcher");
        user.setRole(User.Role.RESEARCHER_STUDENT);
        user.setActive(true);

        when(userRepository.findByEmail("researcher@campus.edu")).thenReturn(Optional.of(user));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "researcher@campus.edu",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_RESEARCHER_STUDENT"))
        );

        Map<String, Object> result = userController.getCurrentUser(auth);

        assertNotNull(result);
        assertEquals(10L, result.get("id"));
        assertEquals("researcher@campus.edu", result.get("email"));
        assertEquals("Dr. Researcher", result.get("fullName"));
        assertEquals("RESEARCHER_STUDENT", result.get("role"));
    }

    @Test
    @DisplayName("getCurrentUser resolves user from OAuth2AuthenticationToken with Google sub and email attribute")
    void getCurrentUser_oauth2Auth_resolvesByEmailAttribute() {
        User user = new User();
        user.setId(20L);
        user.setEmail("google.user@gmail.com");
        user.setGoogleId("google-sub-777");
        user.setFullName("Google User");
        user.setRole(User.Role.RESEARCHER_STUDENT);
        user.setActive(true);

        OAuth2User oauthUser = mock(OAuth2User.class);
        when(oauthUser.getName()).thenReturn("google-sub-777");
        when(oauthUser.getAttribute("email")).thenReturn("google.user@gmail.com");
        when(oauthUser.getAttribute("sub")).thenReturn("google-sub-777");

        when(userRepository.findByEmail("google.user@gmail.com")).thenReturn(Optional.of(user));

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                oauthUser,
                List.of(new SimpleGrantedAuthority("SCOPE_openid")),
                "google"
        );

        Map<String, Object> result = userController.getCurrentUser(auth);

        assertNotNull(result);
        assertEquals(20L, result.get("id"));
        assertEquals("google.user@gmail.com", result.get("email"));
    }

    @Test
    @DisplayName("getCurrentUser falls back to GoogleId when OAuth2User email is not found directly")
    void getCurrentUser_oauth2Auth_fallsBackToGoogleId() {
        User user = new User();
        user.setId(30L);
        user.setEmail("different.email@gmail.com");
        user.setGoogleId("google-sub-888");
        user.setFullName("Google Fallback User");
        user.setRole(User.Role.RESEARCHER_STUDENT);
        user.setActive(true);

        OAuth2User oauthUser = mock(OAuth2User.class);
        when(oauthUser.getName()).thenReturn("google-sub-888");
        when(oauthUser.getAttribute("email")).thenReturn("lookup.email@gmail.com");
        when(oauthUser.getAttribute("sub")).thenReturn("google-sub-888");

        when(userRepository.findByEmail("lookup.email@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByGoogleId("google-sub-888")).thenReturn(Optional.of(user));

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                oauthUser,
                List.of(new SimpleGrantedAuthority("SCOPE_openid")),
                "google"
        );

        Map<String, Object> result = userController.getCurrentUser(auth);

        assertNotNull(result);
        assertEquals(30L, result.get("id"));
        assertEquals("different.email@gmail.com", result.get("email"));
    }

    @Test
    @DisplayName("getCurrentUser throws 401 when user is inactive")
    void getCurrentUser_inactiveUser_throws401() {
        User user = new User();
        user.setEmail("inactive@campus.edu");
        user.setActive(false);

        when(userRepository.findByEmail("inactive@campus.edu")).thenReturn(Optional.of(user));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "inactive@campus.edu",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_RESEARCHER_STUDENT"))
        );

        assertThrows(ResponseStatusException.class, () -> userController.getCurrentUser(auth));
    }

    @Test
    @DisplayName("getCurrentUser throws 401 when authentication is null or unauthenticated")
    void getCurrentUser_unauthenticated_throws401() {
        assertThrows(ResponseStatusException.class, () -> userController.getCurrentUser(null));

        UsernamePasswordAuthenticationToken unauth = new UsernamePasswordAuthenticationToken("guest", null);
        assertThrows(ResponseStatusException.class, () -> userController.getCurrentUser(unauth));
    }
}

