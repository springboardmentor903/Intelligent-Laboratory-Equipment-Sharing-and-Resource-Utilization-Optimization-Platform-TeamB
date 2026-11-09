
package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.equipment.dto.CategoryRequest;
import com.campus.lab_resource_platform.equipment.entity.EquipmentCategory;
import com.campus.lab_resource_platform.equipment.repository.EquipmentCategoryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/equipment-categories")
public class EquipmentCategoryController {

    private final EquipmentCategoryRepository categoryRepository;

    public EquipmentCategoryController(
            EquipmentCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<EquipmentCategory> getAllCategories() {
        return categoryRepository.findAll(
                org.springframework.data.domain.Sort.by("name"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentCategory createCategory(
            @Valid @RequestBody CategoryRequest request) {

        String name = request.getName().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A category with this name already exists"
            );
        }

        EquipmentCategory category = new EquipmentCategory();
        category.setName(name);
        category.setDescription(request.getDescription());

        return categoryRepository.save(category);
    }
}
