package com.campus.lab_resource_platform.maintenance.controller;

import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestCreateDto;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestResponse;
import com.campus.lab_resource_platform.maintenance.dto.MaintenanceRequestReviewDto;
import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequestStatus;
import com.campus.lab_resource_platform.maintenance.service.MaintenanceRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance/requests")
public class MaintenanceRequestController {

    private final MaintenanceRequestService requestService;

    public MaintenanceRequestController(MaintenanceRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    public ResponseEntity<MaintenanceRequestResponse> submitRequest(
            @Valid @RequestBody MaintenanceRequestCreateDto dto,
            Authentication authentication
    ) {
        MaintenanceRequestResponse response = requestService.submitRequest(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<Page<MaintenanceRequestResponse>> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(requestService.getMyRequests(authentication.getName(), pageable));
    }

    @GetMapping({"", "/queue"})
    public ResponseEntity<Page<MaintenanceRequestResponse>> getManagerQueue(
            @RequestParam(required = false) MaintenanceRequestStatus status,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(requestService.getManagerQueue(status, priority, equipmentId, authentication.getName(), pageable));
    }

    @RequestMapping(value = "/{id}/approve", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<MaintenanceRequestResponse> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceRequestReviewDto reviewDto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requestService.approveRequest(id, reviewDto, authentication.getName()));
    }

    @RequestMapping(value = "/{id}/reject", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<MaintenanceRequestResponse> rejectRequest(
            @PathVariable Long id,
            @RequestBody(required = false) MaintenanceRequestReviewDto reviewDto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requestService.rejectRequest(id, reviewDto, authentication.getName()));
    }
}
