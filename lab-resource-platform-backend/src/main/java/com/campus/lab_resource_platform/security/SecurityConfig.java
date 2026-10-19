package com.campus.lab_resource_platform.security;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public BCryptPasswordEncoder encoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        return http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/api/auth/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/equipment/**")
                        .hasAnyRole(
                                "RESEARCHER_STUDENT",
                                "LAB_TECHNICIAN",
                                "LAB_MANAGER",
                                "DEPARTMENT_HEAD",
                                "INSTITUTION_ADMINISTRATOR",
                                "SYSTEM_ADMINISTRATOR"
                        )
                        .requestMatchers(HttpMethod.POST, "/api/equipment/**")
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

                        .requestMatchers("/api/analytics/**", "/api/reports/**")
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
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5173"
                )
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
        configuration.setAllowedHeaders(
                List.of("*")
        );
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
