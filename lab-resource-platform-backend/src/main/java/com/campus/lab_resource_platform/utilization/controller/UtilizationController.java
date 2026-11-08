package com.campus.lab_resource_platform.utilization.controller;

import com.campus.lab_resource_platform.utilization.dto.CheckInRequest;
import com.campus.lab_resource_platform.utilization.dto.CheckOutRequest;
import com.campus.lab_resource_platform.utilization.dto.UtilizationResponse;
import com.campus.lab_resource_platform.utilization.service.UtilizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/utilization")
public class UtilizationController {

    private final UtilizationService utilizationService;

    public UtilizationController(UtilizationService utilizationService) {
        this.utilizationService = utilizationService;
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public UtilizationResponse checkIn(@Valid @RequestBody CheckInRequest request) {
        return utilizationService.checkIn(request);
    }

    @PostMapping("/check-out")
    public UtilizationResponse checkOut(@Valid @RequestBody CheckOutRequest request) {
        return utilizationService.checkOut(request);
    }
}
