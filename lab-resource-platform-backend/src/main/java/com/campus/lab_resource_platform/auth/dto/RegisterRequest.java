package com.campus.lab_resource_platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank(message = "Role is required")
    @Pattern(
            regexp = "RESEARCHER_STUDENT|LAB_TECHNICIAN|LAB_MANAGER|DEPARTMENT_HEAD|INSTITUTION_ADMINISTRATOR|SYSTEM_ADMINISTRATOR",
            message = "Invalid role"
    )
    private String role = "RESEARCHER_STUDENT";
}