package com.campus.lab_resource_platform.maintenance.controller;

import com.campus.lab_resource_platform.maintenance.dto.*;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import com.campus.lab_resource_platform.maintenance.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/maintenance/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody WorkOrderCreateDto dto,
            Authentication authentication
    ) {
        WorkOrderResponse response = workOrderService.createWorkOrder(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<WorkOrderResponse>> getWorkOrders(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) WorkOrderType type,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(workOrderService.getWorkOrders(status, type, equipmentId, technicianId, authentication.getName(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> getWorkOrderById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.getWorkOrderById(id, authentication.getName()));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<WorkOrderResponse> assignTechnician(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderAssignDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.assignTechnician(id, dto, authentication.getName()));
    }

    @PostMapping("/{id}/start-preview")
    public ResponseEntity<StartPreviewResponse> previewStartWorkOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.previewStartWorkOrder(id, authentication.getName()));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<WorkOrderResponse> startWorkOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.startWorkOrder(id, authentication.getName()));
    }

    @PostMapping("/{id}/extend")
    public ResponseEntity<WorkOrderResponse> extendWorkOrder(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderExtendDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.extendWorkOrder(id, dto, authentication.getName()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<WorkOrderResponse> completeWorkOrder(
            @PathVariable Long id,
            @RequestBody(required = false) WorkOrderCompleteDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.completeWorkOrder(id, dto, authentication.getName()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkOrderResponse> cancelWorkOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(workOrderService.cancelWorkOrder(id, reason, authentication.getName()));
    }

    @PostMapping("/{id}/logs")
    public ResponseEntity<WorkOrderServiceLogDto> addServiceLog(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String note = body != null ? body.get("note") : "";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workOrderService.addServiceLogNote(id, note, authentication.getName()));
    }
}
