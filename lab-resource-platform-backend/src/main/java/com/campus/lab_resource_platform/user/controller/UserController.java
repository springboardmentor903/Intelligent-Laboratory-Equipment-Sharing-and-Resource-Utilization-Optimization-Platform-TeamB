package com.campus.lab_resource_platform.user.controller;

import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;

    public UserController(UserRepository userRepository, CollegeRepository collegeRepository) {
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
    }

    @GetMapping("/users/me")
    public Map<String, Object> getCurrentUser(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Not authenticated"
            );
        }

        User user = userRepository.findByEmail(
                authentication.getName()
        ).filter(User::isActive)
         .orElseThrow(() -> new ResponseStatusException(
                 HttpStatus.NOT_FOUND,
                 "User not found or inactive"
         ));

        Long cId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        String cName = null;
        if (cId != null) {
            cName = collegeRepository.findById(cId).map(College::getName).orElse(null);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", user.getId());
        resp.put("fullName", user.getFullName());
        resp.put("email", user.getEmail());
        resp.put("role", user.getRole().name());
        resp.put("collegeId", cId);
        resp.put("collegeName", cName);
        resp.put("profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl());

        return resp;
    }
}
