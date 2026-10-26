
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
            GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler
    ) {
        this.jwtFilter = jwtFilter;
        this.googleOAuth2SuccessHandler = googleOAuth2SuccessHandler;
    }

    @Bean
    public BCryptPasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())

                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) ->
                                        response.sendError(
                                                jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
                        ),
                                request -> {
                                    String path = request.getRequestURI();
                                    return path.startsWith("/api/") || path.startsWith("/auth/");
                                }
                        )
                )
                // Google OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(googleOAuth2SuccessHandler)
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/myregister",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/auth/login",
                                "/auth/register",
                                "/auth/myregister",
                                "/auth/forgot-password",
                                "/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
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

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173," +
                        "http://localhost:5174")
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
