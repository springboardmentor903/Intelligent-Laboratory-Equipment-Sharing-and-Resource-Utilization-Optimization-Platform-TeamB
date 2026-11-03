package com.campus.lab_resource_platform.department.controller;

import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public ResponseEntity<List<Department>> getDepartments(
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Long institutionId
    ) {
        Long targetId = collegeId != null ? collegeId : institutionId;
        if (targetId != null) {
            return ResponseEntity.ok(departmentRepository.findByCollegeIdOrInstitutionId(targetId, targetId));
        }
        return ResponseEntity.ok(departmentRepository.findByActiveTrue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Department> getDepartmentById(@PathVariable Long id) {
        return departmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
