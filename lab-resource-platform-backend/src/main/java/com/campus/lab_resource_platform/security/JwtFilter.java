package com.campus.lab_resource_platform.security;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    public JwtFilter(JwtService jwtService, UserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = null;
        String authorizationHeader = req.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String candidate = authorizationHeader.substring(7).trim();
            if (!candidate.isEmpty() && !"null".equalsIgnoreCase(candidate) && !"undefined".equalsIgnoreCase(candidate)) {
                token = candidate;
            }
        }

        // If no Bearer token or it fails validation, fall back to the "jwt" cookie
        if ((token == null || !jwtService.validateToken(token)) && req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    String cookieVal = cookie.getValue();
                    if (cookieVal != null && !cookieVal.isBlank() && !"null".equalsIgnoreCase(cookieVal)) {
                        token = cookieVal;
                        break;
                    }
                }
            }
        }

        if (token != null && jwtService.validateToken(token)) {
            String email = jwtService.extractEmailByToken(token);

            // Validate against current database record so that revoked, inactive, or
            // role-changed accounts are strictly governed by the live database state.
            Optional<User> optionalUser = userRepo.findByEmail(email.toLowerCase());
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                if (user.isActive()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user.getEmail(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                            );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(req, res);
    }
}
