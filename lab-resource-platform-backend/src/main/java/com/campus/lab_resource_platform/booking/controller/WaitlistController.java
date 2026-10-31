package com.campus.lab_resource_platform.booking.controller;

import com.campus.lab_resource_platform.booking.dto.WaitlistRequest;
import com.campus.lab_resource_platform.booking.dto.WaitlistResponse;
import com.campus.lab_resource_platform.booking.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    public ResponseEntity<WaitlistResponse> joinWaitlist(
            @Valid @RequestBody WaitlistRequest request,
            Authentication authentication
    ) {
        WaitlistResponse response = waitlistService.joinWaitlist(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<WaitlistResponse>> getMyWaitlist(Authentication authentication) {
        List<WaitlistResponse> response = waitlistService.getMyWaitlist(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<List<WaitlistResponse>> getEquipmentWaitlist(@PathVariable Long equipmentId) {
        List<WaitlistResponse> response = waitlistService.getEquipmentWaitlist(equipmentId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> leaveWaitlist(
            @PathVariable Long id,
            Authentication authentication
    ) {
        waitlistService.leaveWaitlist(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

