package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.equipment.dto.DocumentRequest;
import com.campus.lab_resource_platform.equipment.entity.Document;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.DocumentRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;

import java.util.List;

@RestController
@RequestMapping("/api/equipment/{equipmentId}/documents")
public class DocumentController {

    private final EquipmentRepository equipmentRepository;
    private final DocumentRepository documentRepository;
    private final EquipmentService equipmentService;

    public DocumentController(
        EquipmentRepository equipmentRepository,
        DocumentRepository documentRepository,
        EquipmentService equipmentService) {

    this.equipmentRepository = equipmentRepository;
    this.documentRepository = documentRepository;
    this.equipmentService = equipmentService;
}

    @GetMapping
    public List<Document> getDocuments(@PathVariable Long equipmentId) {
        findEquipment(equipmentId);

        return documentRepository
                .findByEquipmentIdOrderByUploadedAtDesc(equipmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document createDocument(
            @PathVariable Long equipmentId,
            @Valid @RequestBody DocumentRequest request) {

        equipmentService.authorizeEquipmentManagementFor(equipmentId);
        Equipment equipment = findEquipment(equipmentId);

        Document document = new Document();
        document.setEquipment(equipment);
        document.setTitle(request.getTitle());
        document.setFileName(request.getFileName());
        document.setContentType(request.getContentType());
        document.setFileUrl(request.getFileUrl());

        return documentRepository.save(document);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable Long equipmentId,
            @PathVariable Long documentId) {
            equipmentService.authorizeEquipmentManagementFor(equipmentId);

        Document document = documentRepository
                .findByIdAndEquipmentId(documentId, equipmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document not found for this equipment"));

        documentRepository.delete(document);
    }

    private Equipment findEquipment(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Equipment not found: " + equipmentId));
    }
}