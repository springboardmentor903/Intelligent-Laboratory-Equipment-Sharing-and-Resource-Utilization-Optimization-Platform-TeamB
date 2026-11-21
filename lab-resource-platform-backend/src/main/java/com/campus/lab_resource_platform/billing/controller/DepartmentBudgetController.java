package com.campus.lab_resource_platform.billing.controller;

import com.campus.lab_resource_platform.billing.dto.DepartmentBudgetRequest;
import com.campus.lab_resource_platform.billing.dto.DepartmentBudgetResponse;
import com.campus.lab_resource_platform.billing.service.DepartmentBudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing/budgets")
public class DepartmentBudgetController {

    private final DepartmentBudgetService budgetService;

    public DepartmentBudgetController(DepartmentBudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<DepartmentBudgetResponse> createBudget(
            @Valid @RequestBody DepartmentBudgetRequest request,
            Authentication authentication
    ) {
        DepartmentBudgetResponse response = budgetService.createBudget(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentBudgetResponse> updateBudget(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentBudgetRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(budgetService.updateBudget(id, request, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<DepartmentBudgetResponse>> getBudgets(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Long departmentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(budgetService.getBudgets(fiscalYear, departmentId, authentication.getName()));
    }
}
