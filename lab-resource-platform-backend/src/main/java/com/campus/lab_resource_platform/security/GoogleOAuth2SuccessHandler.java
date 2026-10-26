
package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public GoogleOAuth2SuccessHandler(
            UserRepository userRepository,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();

        String googleId = googleUser.getAttribute("sub");
        String email = googleUser.getAttribute("email");
        String name = googleUser.getAttribute("name");
        String picture = googleUser.getAttribute("picture");

        if (googleId == null || email == null || name == null
                || !Boolean.TRUE.equals(googleUser.getAttribute("email_verified"))) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Google account information is incomplete or unverified"
            );
            return;
        }

        final String normalizedEmail = email.trim().toLowerCase();

        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .map(existingUser -> {
                    if (existingUser.getGoogleId() == null) {
                        existingUser.setGoogleId(googleId);
                    }
                    if (existingUser.getProfileImageUrl() == null && picture != null) {
                        existingUser.setProfileImageUrl(picture);
                    }
                    existingUser.setEmailVerified(true);
                    return existingUser;
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setGoogleId(googleId);
                    newUser.setEmail(normalizedEmail);
                    newUser.setFullName(name);
                    newUser.setProfileImageUrl(picture);
                    newUser.setAuthProvider(User.AuthProvider.GOOGLE);
                    newUser.setEmailVerified(true);
                    newUser.setActive(true);
                    newUser.setRole(User.Role.RESEARCHER_STUDENT);
                    return newUser;
                });

        if (!user.isActive()) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "This account is disabled"
            );
            return;
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getRole().name()
        );

        jakarta.servlet.http.Cookie jwtCookie =
        new jakarta.servlet.http.Cookie("jwt", token);

        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(60 * 60);
        jwtCookie.setSecure(false); // Local HTTP development only

        response.addCookie(jwtCookie);

        response.sendRedirect("http://localhost:5173/oauth/callback");
    }
}
