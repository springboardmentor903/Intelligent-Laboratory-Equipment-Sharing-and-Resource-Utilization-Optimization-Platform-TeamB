package com.campus.lab_resource_platform.storage.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadResponse {

    private Long documentId;
    private Long equipmentId;
    private String title;
    private String fileName;
    private String fileReference;
    private String fileUrl;
    private String contentType;
    private Long fileSize;
    private Instant uploadedAt;
}
