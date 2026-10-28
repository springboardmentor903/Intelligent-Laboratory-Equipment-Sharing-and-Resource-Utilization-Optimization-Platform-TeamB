package com.campus.lab_resource_platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    private String token;

    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    private String confirmPassword;
}
