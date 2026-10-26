package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByEquipmentIdOrderByUploadedAtDesc(Long equipmentId);

    Optional<Document> findByIdAndEquipmentId(Long id, Long equipmentId);
}