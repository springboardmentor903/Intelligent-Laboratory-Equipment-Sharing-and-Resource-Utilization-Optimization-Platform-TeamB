
package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String defaultFrontendUrl;

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

        // Resolve frontend redirect target dynamically if the frontend provided its origin
        String targetFrontendUrl = defaultFrontendUrl;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("app_frontend_url".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    String candidate = c.getValue().trim();
                    if (candidate.startsWith("http://localhost:") || candidate.startsWith("http://127.0.0.1:")) {
                        targetFrontendUrl = candidate;
                    }
                    break;
                }
            }
        }

        // Clear the transient app_frontend_url cookie
        ResponseCookie clearOriginCookie = ResponseCookie.from("app_frontend_url", "")
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearOriginCookie.toString());

        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();

        String googleId = googleUser.getAttribute("sub");
        String email = googleUser.getAttribute("email");
        String name = googleUser.getAttribute("name");
        String picture = googleUser.getAttribute("picture");

        if (googleId == null || email == null || name == null
                || !Boolean.TRUE.equals(googleUser.getAttribute("email_verified"))) {
            response.sendRedirect(targetFrontendUrl + "/oauth/callback?error=unverified_google_account");
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
            response.sendRedirect(targetFrontendUrl + "/oauth/callback?error=account_inactive");
            return;
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getRole().name()
        );

        ResponseCookie jwtCookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .secure(false) // Local HTTP development only
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        response.sendRedirect(targetFrontendUrl + "/oauth/callback");
    }
}
