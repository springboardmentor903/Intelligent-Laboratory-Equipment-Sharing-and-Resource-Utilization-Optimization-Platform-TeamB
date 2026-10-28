package com.campus.lab_resource_platform.security;

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

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws ServletException, IOException{
        String token = null;
        String authorizationHeader = req.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7).trim();
        }

        Cookie[] cookies = req.getCookies();
        if (token == null && cookies != null) {
            for(Cookie cookie : cookies){
                if(cookie.getName().equals("jwt")){
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token != null && jwtService.validateToken(token)) {
            String email = jwtService.extractEmailByToken(token);
            String role = jwtService.extractRoleByToken(token);

            if (email != null && role != null) {
                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        email.trim().toLowerCase(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(authority)
                        )
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(req, res);
    }
}
