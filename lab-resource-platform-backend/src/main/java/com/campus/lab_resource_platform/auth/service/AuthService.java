package com.campus.lab_resource_platform.booking.service;

import org.springframework.stereotype.Service;

import com.campus.lab_resource_platform.booking.dto.RegisterRequest;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private BCryptPasswordEncoder encoder;

    public User register(RegisterRequest registerRequest) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email is already registered");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword()));

        user.setAuthProvider(User.AuthProvider.LOCAL);
        user.setRole(User.Role.RESEARCHER_STUDENT);
        user.setEmailVerified(false);
        user.setActive(true);

        return userRepository.save(user);
    }
}
