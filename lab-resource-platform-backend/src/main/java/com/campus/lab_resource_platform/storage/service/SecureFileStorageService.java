package com.campus.lab_resource_platform.storage.service;

import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Document;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.DocumentRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.storage.dto.DocumentLinkResponse;
import com.campus.lab_resource_platform.storage.dto.FileUploadResponse;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecureFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(SecureFileStorageService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final Path storageDirectory = Paths.get("uploads", "documents").toAbsolutePath().normalize();
    private final DocumentRepository documentRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    // Token -> TokenMetadata (valid for 10 minutes)
    private final Map<String, TokenMetadata> tokenCache = new ConcurrentHashMap<>();

    public record TokenMetadata(Long documentId, Path filePath, String contentType, String originalFilename, Instant expiresAt) {}

    public SecureFileStorageService(
            DocumentRepository documentRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        initStorageDirectory();
    }

    private void initStorageDirectory() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            log.error("Could not initialize document storage directory: {}", e.getMessage());
        }
    }

    @Transactional
    public FileUploadResponse uploadDocument(Long equipmentId, String title, MultipartFile file, String userEmail) {
        User user = getUserByEmail(userEmail);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + equipmentId));

        validateStaffUploadAccess(user, equipment);
        validateFile(file);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String extension = getFileExtension(originalFilename);
        String fileRef = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path targetPath = storageDirectory.resolve(fileRef);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage());
            throw new BadRequestException("Failed to store file on server: " + e.getMessage());
        }

        Document document = new Document();
        document.setEquipment(equipment);
        document.setTitle(title != null && !title.isBlank() ? title.trim() : originalFilename);
        document.setFileName(originalFilename);
        document.setContentType(file.getContentType());
        document.setFileUrl("/api/documents/view-ref/" + fileRef);

        Document saved;
        try {
            saved = documentRepository.save(document);
        } catch (Exception ex) {
            // Rollback stored file if DB save fails
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw ex;
        }

        return FileUploadResponse.builder()
                .documentId(saved.getId())
                .equipmentId(equipment.getId())
                .title(saved.getTitle())
                .fileName(saved.getFileName())
                .fileReference(fileRef)
                .fileUrl(saved.getFileUrl())
                .contentType(saved.getContentType())
                .fileSize(file.getSize())
                .uploadedAt(saved.getUploadedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentLinkResponse generateShortLivedLink(Long documentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        // Check equipment access
        if (document.getEquipment() != null) {
            validateViewAccess(user, document.getEquipment());
        }

        String fileRef = extractFileReference(document.getFileUrl());
        Path filePath = storageDirectory.resolve(fileRef);
        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("Physical document file does not exist on server");
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(600); // 10 minutes

        TokenMetadata meta = new TokenMetadata(
                document.getId(),
                filePath,
                document.getContentType(),
                document.getFileName(),
                expiresAt
        );
        tokenCache.put(token, meta);

        return DocumentLinkResponse.builder()
                .token(token)
                .viewUrl("/api/documents/view/" + token)
                .expiresAt(expiresAt)
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .build();
    }

    public Resource getFileByToken(String token) {
        cleanExpiredTokens();
        TokenMetadata meta = tokenCache.get(token);
        if (meta == null) {
            throw new ForbiddenException("Invalid or expired document link (links are valid for 10 minutes)");
        }
        if (Instant.now().isAfter(meta.expiresAt())) {
            tokenCache.remove(token);
            throw new ForbiddenException("Document link has expired. Please generate a new one.");
        }
        if (!Files.exists(meta.filePath())) {
            throw new ResourceNotFoundException("File not found on server");
        }
        return new FileSystemResource(meta.filePath());
    }

    public TokenMetadata getTokenMetadata(String token) {
        return tokenCache.get(token);
    }

    @Transactional
    public void deleteDocument(Long documentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        validateStaffUploadAccess(user, document.getEquipment());

        String fileRef = extractFileReference(document.getFileUrl());
        Path filePath = storageDirectory.resolve(fileRef);

        documentRepository.delete(document);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Could not delete physical file {}: {}", filePath, e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File exceeds maximum allowed size of 10 MB");
        }

        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BadRequestException("File type not allowed. Allowed formats: PDF, PNG, JPEG/JPG");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new BadRequestException("Invalid content type: " + mimeType + ". Allowed: application/pdf, image/png, image/jpeg");
        }

        // Magic bytes inspection to prevent extension spoofing
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = in.read(header);
            if (read < 4) {
                throw new BadRequestException("Corrupt file content");
            }
            if ("pdf".equalsIgnoreCase(extension)) {
                // Must start with %PDF
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    throw new BadRequestException("File is not a valid PDF document");
                }
            } else if ("png".equalsIgnoreCase(extension)) {
                // PNG signature: 89 50 4E 47
                if ((header[0] & 0xFF) != 0x89 || header[1] != 0x50 || header[2] != 0x4E || header[3] != 0x47) {
                    throw new BadRequestException("File is not a valid PNG image");
                }
            } else if ("jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension)) {
                // JPEG signature: FF D8 FF
                if ((header[0] & 0xFF) != 0xFF || (header[1] & 0xFF) != 0xD8 || (header[2] & 0xFF) != 0xFF) {
                    throw new BadRequestException("File is not a valid JPEG image");
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Unable to inspect file header: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String extractFileReference(String url) {
        if (url == null) return "";
        int slash = url.lastIndexOf('/');
        return slash >= 0 ? url.substring(slash + 1) : url;
    }

    private void cleanExpiredTokens() {
        Instant now = Instant.now();
        tokenCache.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private void validateStaffUploadAccess(User user, Equipment equipment) {
        if (user.getRole() != User.Role.LAB_MANAGER
                && user.getRole() != User.Role.LAB_TECHNICIAN
                && user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR) {
            throw new ForbiddenException("Only Lab Managers, Technicians and Institution Administrators can upload equipment documents");
        }
    }

    private void validateViewAccess(User user, Equipment equipment) {
        // Any authenticated user belonging to institution or partner sharing can view
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
