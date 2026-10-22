package com.campus.lab_resource_platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(max = 72)
    private String password;
}
