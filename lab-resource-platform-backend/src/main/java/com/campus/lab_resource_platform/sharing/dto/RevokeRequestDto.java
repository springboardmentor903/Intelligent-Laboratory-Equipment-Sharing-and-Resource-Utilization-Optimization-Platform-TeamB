package com.campus.lab_resource_platform.sharing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevokeRequestDto {

    @NotBlank(message = "Revocation reason is required")
    private String reason;
}

