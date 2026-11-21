package com.campus.lab_resource_platform.storage.service;

import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.equipment.entity.Document;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.DocumentRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.storage.dto.DocumentLinkResponse;
import com.campus.lab_resource_platform.storage.dto.FileUploadResponse;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecureFileStorageServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SecureFileStorageService storageService;

    private User labManager;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        labManager = new User();
        labManager.setId(1L);
        labManager.setEmail("manager@lab.edu");
        labManager.setRole(User.Role.LAB_MANAGER);
        labManager.setInstitutionId(10L);

        equipment = new Equipment();
        equipment.setId(100L);
        equipment.setName("Gas Chromatograph");
        equipment.setInstitutionId(10L);
    }

    @Test
    void uploadDocument_RejectsOversizedFile() {
        // Exceeds 10MB
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "manual.pdf", "application/pdf", largeBytes);

        when(userRepository.findByEmail(labManager.getEmail())).thenReturn(Optional.of(labManager));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));

        assertThrows(BadRequestException.class, () ->
                storageService.uploadDocument(100L, "MANUAL", file, labManager.getEmail()));
    }

    @Test
    void uploadDocument_RejectsDisallowedFileType() {
        byte[] bytes = "echo hello".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "script.sh", "text/plain", bytes);

        when(userRepository.findByEmail(labManager.getEmail())).thenReturn(Optional.of(labManager));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));

        assertThrows(BadRequestException.class, () ->
                storageService.uploadDocument(100L, "MANUAL", file, labManager.getEmail()));
    }

    @Test
    void uploadDocument_RejectsMismatchedMagicBytes() {
        // Claimed to be PDF, but content is dummy text
        byte[] fakePdfBytes = "Not a real PDF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", fakePdfBytes);

        when(userRepository.findByEmail(labManager.getEmail())).thenReturn(Optional.of(labManager));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));

        assertThrows(BadRequestException.class, () ->
                storageService.uploadDocument(100L, "MANUAL", file, labManager.getEmail()));
    }

    @Test
    void uploadDocument_ValidPdf_Success() {
        // Valid PDF magic bytes: %PDF-1.4
        byte[] validPdf = "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", validPdf);

        when(userRepository.findByEmail(labManager.getEmail())).thenReturn(Optional.of(labManager));
        when(equipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            Document doc = i.getArgument(0);
            doc.setId(42L);
            return doc;
        });

        FileUploadResponse res = storageService.uploadDocument(100L, "CERTIFICATE", file, labManager.getEmail());

        assertNotNull(res);
        assertEquals(42L, res.getDocumentId());
        assertEquals("certificate.pdf", res.getFileName());
        assertEquals("application/pdf", res.getContentType());
    }
}
