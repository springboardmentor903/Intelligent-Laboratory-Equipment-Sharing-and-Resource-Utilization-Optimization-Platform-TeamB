
package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.equipment.dto.SpecificationRequest;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.Specification;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.equipment.repository.SpecificationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;

import java.util.List;

@RestController
@RequestMapping("/api/equipment/{equipmentId}/specifications")
public class SpecificationController {

    private final EquipmentRepository equipmentRepository;
    private final SpecificationRepository specificationRepository;
    private final EquipmentService equipmentService;

    public SpecificationController(
        EquipmentRepository equipmentRepository,
        SpecificationRepository specificationRepository,
        EquipmentService equipmentService) {

    this.equipmentRepository = equipmentRepository;
    this.specificationRepository = specificationRepository;
    this.equipmentService = equipmentService;
}

    @GetMapping
    public List<Specification> getSpecifications(
            @PathVariable Long equipmentId) {

        findEquipment(equipmentId);
        return specificationRepository.findByEquipmentId(equipmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Specification createSpecification(
            @PathVariable Long equipmentId,
            @Valid @RequestBody SpecificationRequest request) {

        equipmentService.authorizeEquipmentManagementFor(equipmentId);
        Equipment equipment = findEquipment(equipmentId);

        Specification specification = new Specification();
        specification.setEquipment(equipment);
        specification.setName(request.getName().trim());
        specification.setValue(request.getValue().trim());
        specification.setUnit(
                request.getUnit() == null ? null : request.getUnit().trim());

        return specificationRepository.save(specification);
    }

    @DeleteMapping("/{specificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSpecification(
            @PathVariable Long equipmentId,
            @PathVariable Long specificationId) {
             equipmentService.authorizeEquipmentManagementFor(equipmentId);   

        Specification specification = specificationRepository
                .findByIdAndEquipmentId(specificationId, equipmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Specification not found for this equipment"));

        specificationRepository.delete(specification);
    }

    private Equipment findEquipment(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Equipment not found: " + equipmentId));
    }
}
