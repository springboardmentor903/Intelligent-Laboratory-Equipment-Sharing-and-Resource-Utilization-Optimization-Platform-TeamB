package com.campus.lab_resource_platform.storage.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentLinkResponse {

    private String token;
    private String viewUrl;
    private Instant expiresAt;
    private String fileName;
    private String contentType;
}
