
package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.equipment.dto.EquipmentRequest;
import com.campus.lab_resource_platform.equipment.dto.EquipmentResponse;
import com.campus.lab_resource_platform.equipment.dto.EquipmentStatusRequest;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public Page<EquipmentResponse> getEquipment(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword) {

        return equipmentService.getEquipment(
                page, size, status, categoryId,
                tagId, departmentId, keyword);
    }

    @GetMapping("/{id}")
    public EquipmentResponse getEquipmentById(@PathVariable Long id) {
        return equipmentService.getEquipmentById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponse createEquipment(
            @Valid @RequestBody EquipmentRequest request) {
        return equipmentService.createEquipment(request);
    }

    @PutMapping("/{id}")
    public EquipmentResponse updateEquipment(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentRequest request) {
        return equipmentService.updateEquipment(id, request);
    }

    @PatchMapping("/{id}/status")
    public EquipmentResponse updateEquipmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentStatusRequest request) {
        return equipmentService.updateEquipmentStatus(id, request);
    }
}
