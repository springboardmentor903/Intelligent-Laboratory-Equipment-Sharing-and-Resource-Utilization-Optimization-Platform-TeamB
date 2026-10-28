package com.campus.lab_resource_platform;

import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class StaffAccountsInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;
    private final Environment environment;

    public StaffAccountsInitializer(
            UserRepository userRepo,
            BCryptPasswordEncoder encoder,
            Environment environment) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        createStaffAccount(
                "TECHNICIAN_EMAIL",
                "TECHNICIAN_PASSWORD",
                User.Role.LAB_TECHNICIAN,
                "Lab Technician"
        );

        createStaffAccount(
                "LAB_MANAGER_EMAIL",
                "LAB_MANAGER_PASSWORD",
                User.Role.LAB_MANAGER,
                "Lab Manager"
        );

        createStaffAccount(
                "DEPARTMENT_HEAD_EMAIL",
                "DEPARTMENT_HEAD_PASSWORD",
                User.Role.DEPARTMENT_HEAD,
                "Department Head"
        );

        createStaffAccount(
                "INSTITUTION_ADMIN_EMAIL",
                "INSTITUTION_ADMIN_PASSWORD",
                User.Role.INSTITUTION_ADMINISTRATOR,
                "Institution Administrator"
        );
    }

    private void createStaffAccount(
            String emailKey,
            String passwordKey,
            User.Role role,
            String fullName) {

        String email = environment.getProperty(emailKey);
        String password = environment.getProperty(passwordKey);

        if ((email == null || email.isBlank()) && role == User.Role.LAB_MANAGER) {
            email = "live.manager@campus.edu";
            password = "LiveManagerPass123!";
        }

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            System.out.println(
                    "Credentials not configured for role: " + role);
            return;
        }

        email = email.trim().toLowerCase();

        if (userRepo.existsByEmail(email)) {
            System.out.println(
                    "Account already exists for role: " + role);
            return;
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setAuthProvider(User.AuthProvider.LOCAL);
        user.setRole(role);
        user.setEmailVerified(true);
        user.setActive(true);

        userRepo.save(user);

        System.out.println(
                "Created initial account for role: " + role);
    }
}
