
package com.campus.lab_resource_platform.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;

    public SecurityConfig(
            JwtFilter jwtFilter,
            GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler
    ) {
        this.jwtFilter = jwtFilter;
        this.googleOAuth2SuccessHandler = googleOAuth2SuccessHandler;
    }

    @Bean
    public BCryptPasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    public static RequestMatcher createApiRequestMatcher() {
        return request -> {
            String path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path != null && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
            if (path == null) {
                return false;
            }
            boolean isApiPath = path.startsWith("/api/") || path.equals("/api")
                    || path.startsWith("/auth/") || path.equals("/auth");
            boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
            String accept = request.getHeader("Accept");
            boolean acceptsJson = accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);

            return isApiPath || isAjax || acceptsJson;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        RequestMatcher apiRequestMatcher = createApiRequestMatcher();

        return http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())

                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) -> {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                                },
                                apiRequestMatcher
                        )
                        .defaultAccessDeniedHandlerFor(
                                (request, response, accessDeniedException) -> {
                                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                                },
                                apiRequestMatcher
                        )
                )
                // Google OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(googleOAuth2SuccessHandler)
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/mylogin",
                                "/api/auth/register",
                                "/api/auth/myregister",
                                "/api/auth/forgot-password",
                                "/api/auth/forgot-password/**",
                                "/api/auth/reset-password",
                                "/api/auth/logout",
                                "/auth/login",
                                "/auth/mylogin",
                                "/auth/register",
                                "/auth/myregister",
                                "/auth/forgot-password",
                                "/auth/forgot-password/**",
                                "/auth/reset-password",
                                "/auth/logout",
                                "/api/technician-requests",
                                "/api/technician-requests/status"
                        ).permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        .requestMatchers(
                                "/error"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/equipment/**"
                        )
                        .hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/equipment/**"
                        )
                        .hasAnyRole(
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers("/api/equipment/**")
                        .hasAnyRole(
                                "LAB_MANAGER",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers("/api/bookings/**")
                        .hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers("/api/maintenance/**")
                        .hasAnyRole(
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers(
                                "/api/analytics/**",
                                "/api/reports/**"
                        )
                        .hasAnyRole(
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )

                        .requestMatchers("/api/admin/**")
                        .hasRole("SYSTEM_ADMINISTRATOR")

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(
                List.of(
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]"
                )
        );

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
                        "DELETE",
                        "PATCH",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Set-Cookie", "Authorization"));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
