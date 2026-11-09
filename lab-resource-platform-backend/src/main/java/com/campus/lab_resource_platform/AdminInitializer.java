package com.campus.lab_resource_platform;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;
    private final Environment environment;

    public AdminInitializer(
            UserRepository userRepo,
            BCryptPasswordEncoder encoder,
            Environment environment) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String email = environment.getProperty("ADMIN_EMAIL");
        String password = environment.getProperty("ADMIN_PASSWORD");

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            System.out.println("Admin credentials not configured.");
            return;
        }

        email = email.trim().toLowerCase();

        if (userRepo.findByEmail(email).isPresent()) {
            System.out.println("Admin account already exists.");
            return;
        }

        User admin = new User();
        admin.setFullName("System Administrator");
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(password));
        admin.setAuthProvider(User.AuthProvider.LOCAL);
        admin.setRole(User.Role.SYSTEM_ADMINISTRATOR);
        admin.setEmailVerified(true);
        admin.setActive(true);

        userRepo.save(admin);
        System.out.println("System administrator created successfully.");
    }
}