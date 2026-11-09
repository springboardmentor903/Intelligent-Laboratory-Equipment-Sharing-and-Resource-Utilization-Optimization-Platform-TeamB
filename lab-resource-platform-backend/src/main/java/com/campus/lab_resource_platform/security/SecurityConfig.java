package com.campus.lab_resource_platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;

    public SecurityConfig(
            JwtFilter jwtFilter,
            GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.googleOAuth2SuccessHandler = googleOAuth2SuccessHandler;
    }

    @Bean
    public BCryptPasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        return http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required or session expired. Please sign in again.\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied: insufficient permissions.\"}");
                        })
                )
                // Google OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(googleOAuth2SuccessHandler))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html").permitAll()

                        // Public authentication and OAuth2 endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(
                                "/auth/login",
                                "/auth/register",
                                "/auth/myregister",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/api/technician-requests",
                                "/api/technician-requests/**",
                                "/api/access-requests",
                                "/api/access-requests/**",
                                "/api/colleges",
                                "/api/departments",
                                "/api/departments/**",
                                "/ws/utilization",
                                "/ws/utilization/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // All six roles can view equipment
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/equipment",
                                "/api/equipment/**",
                                "/api/equipment-categories",
                                "/api/equipment-categories/**",
                                "/api/tags",
                                "/api/tags/**"
                        ).hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Only equipment managers can create or modify equipment-related data
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/equipment/**"
                        ).hasAnyRole(
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/equipment/**"
                        ).hasAnyRole(
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers("/api/utilization/**")
                        .hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/equipment/**"
                        ).hasAnyRole(
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/equipment/**"
                        ).hasAnyRole(
                                "LAB_MANAGER",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Category and tag creation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/equipment-categories/**",
                                "/api/tags/**"
                        ).hasAnyRole(
                                "LAB_MANAGER",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Booking APIs
                        .requestMatchers("/api/bookings/**")
                        .hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Sharing Analytics APIs (T6)
                        .requestMatchers("/api/sharing/analytics/**")
                        .hasAnyRole(
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Sharing APIs
                        .requestMatchers("/api/sharing/**")
                        .hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Maintenance APIs
                        .requestMatchers("/api/maintenance/**")
                        .hasAnyRole(
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // Analytics and reports
                        .requestMatchers(
                                "/api/analytics/**",
                                "/api/reports/**"
                        ).hasAnyRole(
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        // System administration
                        .requestMatchers("/api/admin/**")
                        .hasRole("SYSTEM_ADMINISTRATOR")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5173",
                        "http://localhost:5174"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                ));

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
