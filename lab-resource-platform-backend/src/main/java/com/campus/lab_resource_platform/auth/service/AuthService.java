package com.campus.lab_resource_platform.auth.service;

import com.campus.lab_resource_platform.auth.dto.LoginRequest;
import com.campus.lab_resource_platform.auth.dto.RegisterRequest;
import com.campus.lab_resource_platform.security.JwtService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAuthProvider(User.AuthProvider.LOCAL);
        user.setRole(User.Role.RESEARCHER_STUDENT);
        user.setEmailVerified(false);
        user.setActive(true);

        userRepository.save(user);
        return "Registration successful";
    }

    public String login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .filter(User::isActive)
                .filter(account -> account.getAuthProvider() == User.AuthProvider.LOCAL)
                .filter(account -> account.getPasswordHash() != null
                        && passwordEncoder.matches(request.getPassword(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return jwtService.generateToken(user.getEmail(), user.getRole().name());
    }
}
