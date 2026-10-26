
package com.campus.lab_resource_platform.user.controller;

import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import com.campus.lab_resource_platform.user.service.TechnicianAccessRequestService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/technician-requests")
public class TechnicianAccessRequestController {

    private final TechnicianAccessRequestService requestService;

    public TechnicianAccessRequestController(
            TechnicianAccessRequestService requestService) {
        this.requestService = requestService;
    }

    public record SubmitRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 150) String institution,
            @Size(max = 1000) String reason
    ) {}

    public record RequestResponse(
            Long id,
            String fullName,
            String email,
            String status
    ) {
        static RequestResponse from(TechnicianAccessRequest request) {
            return new RequestResponse(
                    request.getId(),
                    request.getFullName(),
                    request.getEmail(),
                    request.getStatus().name()
            );
        }
    }

    @PostMapping
    public ResponseEntity<RequestResponse> submit(
            @Valid @RequestBody SubmitRequest body) {

        TechnicianAccessRequest request =
                requestService.submitRequest(
                        body.fullName(),
                        body.email(),
                        body.institution(),
                        body.reason()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RequestResponse.from(request));
    }
}
