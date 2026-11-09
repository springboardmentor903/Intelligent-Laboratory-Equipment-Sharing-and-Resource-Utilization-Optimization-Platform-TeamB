package com.campus.lab_resource_platform.sharing.controller;

import com.campus.lab_resource_platform.sharing.dto.AccessRequestCreateDto;
import com.campus.lab_resource_platform.sharing.dto.AccessRequestResponse;
import com.campus.lab_resource_platform.sharing.dto.ReviewRemarksDto;
import com.campus.lab_resource_platform.sharing.service.AccessRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sharing/access-requests")
public class AccessRequestController {

    private final AccessRequestService requestService;

    public AccessRequestController(AccessRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestResponse createRequest(@Valid @RequestBody AccessRequestCreateDto dto) {
        return requestService.createRequest(dto);
    }

    @PostMapping("/{id}/forward")
    public AccessRequestResponse forwardRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewRemarksDto dto) {
        return requestService.forwardRequest(id, dto);
    }

    @PostMapping("/{id}/reject-requesting")
    public AccessRequestResponse rejectByRequestingAdmin(
            @PathVariable Long id,
            @RequestBody ReviewRemarksDto dto) {
        return requestService.rejectByRequestingAdmin(id, dto);
    }

    @PostMapping("/{id}/approve")
    public AccessRequestResponse approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewRemarksDto dto) {
        return requestService.approveRequest(id, dto);
    }

    @PostMapping({"/{id}/reject-owner", "/{id}/reject"})
    public AccessRequestResponse rejectByOwnerAdmin(
            @PathVariable Long id,
            @RequestBody ReviewRemarksDto dto) {
        return requestService.rejectByOwnerAdmin(id, dto);
    }

    @PostMapping("/{id}/withdraw")
    public AccessRequestResponse withdrawRequest(@PathVariable Long id) {
        return requestService.withdrawRequest(id);
    }

    @GetMapping("/{id}")
    public AccessRequestResponse getRequestById(@PathVariable Long id) {
        return requestService.getRequestById(id);
    }

    @GetMapping("/my")
    public Page<AccessRequestResponse> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return requestService.getMyRequests(pageable);
    }

    @GetMapping("/department")
    public Page<AccessRequestResponse> getDepartmentRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return requestService.getDepartmentRequests(pageable);
    }

    @GetMapping("/requesting-institution")
    public Page<AccessRequestResponse> getRequestingInstitutionRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return requestService.getRequestingInstitutionRequests(pageable);
    }

    @GetMapping("/owner-institution")
    public Page<AccessRequestResponse> getOwnerInstitutionRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return requestService.getOwnerInstitutionRequests(pageable);
    }
}

