package com.campus.lab_resource_platform.equipment.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Equipment getEquipmentById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Equipment not found: " + id));
    }

    @Transactional
    public Equipment createEquipment(Equipment equipment) {
        equipment.setId(null);
        // All equipment always requires approval (no checkbox, always true)
        equipment.setRequiresApproval(true);
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment updated) {
        Equipment existing = getEquipmentById(id);

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setModel(updated.getModel());
        existing.setManufacturer(updated.getManufacturer());
        existing.setSerialNumber(updated.getSerialNumber());
        existing.setLocation(updated.getLocation());
        existing.setSpecifications(updated.getSpecifications());
        existing.setDocumentationUrl(updated.getDocumentationUrl());
        existing.setTags(updated.getTags());
        existing.setStatus(updated.getStatus());
        existing.setInstitutionId(updated.getInstitutionId());
        existing.setCollegeId(updated.getCollegeId());
        existing.setDepartmentId(updated.getDepartmentId());
        existing.setCalibrationDate(updated.getCalibrationDate());
        existing.setCertificationInfo(updated.getCertificationInfo());
        existing.setMaxBookingHours(updated.getMaxBookingHours());
        existing.setActive(updated.isActive());
        // Always keep requiresApproval = true regardless of what was sent
        existing.setRequiresApproval(true);

        return equipmentRepository.save(existing);
    }

    @Transactional
    public void deleteEquipment(Long id) {
        Equipment existing = getEquipmentById(id);
        equipmentRepository.delete(existing);
    }
}
