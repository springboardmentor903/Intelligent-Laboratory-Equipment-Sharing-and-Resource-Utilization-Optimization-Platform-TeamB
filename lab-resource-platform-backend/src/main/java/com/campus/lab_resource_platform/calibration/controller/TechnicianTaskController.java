package com.campus.lab_resource_platform.calibration.controller;

import com.campus.lab_resource_platform.calibration.dto.TechnicianTaskDto;
import com.campus.lab_resource_platform.calibration.dto.TechnicianWorkloadDto;
import com.campus.lab_resource_platform.calibration.service.TechnicianTaskService;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/technician")
public class TechnicianTaskController {

    private final TechnicianTaskService technicianTaskService;

    public TechnicianTaskController(TechnicianTaskService technicianTaskService) {
        this.technicianTaskService = technicianTaskService;
    }

    @GetMapping("/tasks/my")
    public ResponseEntity<TechnicianTaskDto> getMyTasks(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(technicianTaskService.getMyTasks(status, authentication.getName(), pageable));
    }

    @GetMapping("/workload")
    public ResponseEntity<List<TechnicianWorkloadDto>> getTechnicianWorkload(
            Authentication authentication
    ) {
        return ResponseEntity.ok(technicianTaskService.getTechnicianWorkload(authentication.getName()));
    }
}
