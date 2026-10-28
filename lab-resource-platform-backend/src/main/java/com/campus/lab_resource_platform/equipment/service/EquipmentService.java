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
        if (equipment.getName() == null || equipment.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment name is required");
        }
        equipment.setName(equipment.getName().trim());

        if (equipment.getStatus() == null) {
            equipment.setStatus(com.campus.lab_resource_platform.equipment.entity.EquipmentStatus.AVAILABLE);
        }
        if (equipment.getMaxBookingHours() <= 0) {
            equipment.setMaxBookingHours(8);
        }
        if (equipment.getTags() == null) {
            equipment.setTags(new java.util.HashSet<>());
        }
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment updated) {
        Equipment existing = getEquipmentById(id);

        if (updated.getName() != null && !updated.getName().trim().isEmpty()) {
            existing.setName(updated.getName().trim());
        }
        if (updated.getCategory() != null) {
            existing.setCategory(updated.getCategory().trim().isEmpty() ? null : updated.getCategory().trim());
        }
        if (updated.getModel() != null) {
            existing.setModel(updated.getModel().trim().isEmpty() ? null : updated.getModel().trim());
        }
        if (updated.getManufacturer() != null) {
            existing.setManufacturer(updated.getManufacturer().trim().isEmpty() ? null : updated.getManufacturer().trim());
        }
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        existing.setInstitutionId(updated.getInstitutionId());
        existing.setDepartmentId(updated.getDepartmentId());
        existing.setRequiresApproval(updated.isRequiresApproval());
        if (updated.getMaxBookingHours() > 0) {
            existing.setMaxBookingHours(updated.getMaxBookingHours());
        }
        if (updated.getTags() != null) {
            existing.setTags(new java.util.HashSet<>(updated.getTags()));
        }

        return equipmentRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<String> getAllTags() {
        return equipmentRepository.findAllDistinctTags();
    }

    @Transactional
    public void deleteEquipment(Long id) {
        Equipment existing = getEquipmentById(id);
        equipmentRepository.delete(existing);
    }
}