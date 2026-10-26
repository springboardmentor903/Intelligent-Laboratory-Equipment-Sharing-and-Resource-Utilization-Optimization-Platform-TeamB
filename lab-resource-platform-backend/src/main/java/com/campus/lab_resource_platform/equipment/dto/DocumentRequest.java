package com.campus.lab_resource_platform.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DocumentRequest {

    @NotBlank(message = "Document title is required")
    @Size(max = 150)
    private String title;

    @NotBlank(message = "File name is required")
    @Size(max = 255)
    private String fileName;

    @Size(max = 100)
    private String contentType;

    @NotBlank(message = "File URL is required")
    @Size(max = 1000)
    private String fileUrl;
}