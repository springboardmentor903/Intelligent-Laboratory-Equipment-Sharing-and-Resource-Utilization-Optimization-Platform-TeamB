package com.campus.lab_resource_platform.storage.controller;

import com.campus.lab_resource_platform.storage.dto.DocumentLinkResponse;
import com.campus.lab_resource_platform.storage.dto.FileUploadResponse;
import com.campus.lab_resource_platform.storage.service.SecureFileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentStorageController {

    private final SecureFileStorageService storageService;

    public DocumentStorageController(SecureFileStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadDocument(
            @RequestParam("equipmentId") Long equipmentId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        FileUploadResponse response = storageService.uploadDocument(equipmentId, title, file, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/equipment/{equipmentId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadToEquipment(
            @PathVariable Long equipmentId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        FileUploadResponse response = storageService.uploadDocument(equipmentId, title, file, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{documentId}/link")
    public ResponseEntity<DocumentLinkResponse> getShortLivedLink(
            @PathVariable Long documentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(storageService.generateShortLivedLink(documentId, authentication.getName()));
    }

    @GetMapping("/view/{token}")
    public ResponseEntity<Resource> viewDocument(@PathVariable String token) {
        Resource resource = storageService.getFileByToken(token);
        SecureFileStorageService.TokenMetadata meta = storageService.getTokenMetadata(token);

        String contentType = meta != null && meta.contentType() != null
                ? meta.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String filename = meta != null && meta.originalFilename() != null
                ? meta.originalFilename()
                : "document";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId,
            Authentication authentication
    ) {
        storageService.deleteDocument(documentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
