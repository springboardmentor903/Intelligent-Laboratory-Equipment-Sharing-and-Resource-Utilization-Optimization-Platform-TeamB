package com.campus.lab_resource_platform.auth.controller;

import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public String register(@Valid @RequestBody RegisterRequest registerRequest) {
        return authService.register(registerRequest);
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }
}
