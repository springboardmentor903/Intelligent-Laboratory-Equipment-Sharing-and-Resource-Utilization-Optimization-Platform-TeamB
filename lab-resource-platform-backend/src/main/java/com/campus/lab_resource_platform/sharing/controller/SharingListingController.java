package com.campus.lab_resource_platform.sharing.controller;

import com.campus.lab_resource_platform.sharing.dto.SharingListingDiscoveryResponse;
import com.campus.lab_resource_platform.sharing.dto.SharingListingRequest;
import com.campus.lab_resource_platform.sharing.dto.SharingListingResponse;
import com.campus.lab_resource_platform.sharing.service.SharingListingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sharing")
public class SharingListingController {

    private final SharingListingService listingService;

    public SharingListingController(SharingListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping("/listings")
    @ResponseStatus(HttpStatus.CREATED)
    public SharingListingResponse createListing(@Valid @RequestBody SharingListingRequest request) {
        return listingService.createListing(request);
    }

    @PutMapping("/listings/{id}")
    public SharingListingResponse updateListing(
            @PathVariable Long id,
            @Valid @RequestBody SharingListingRequest request) {
        return listingService.updateListing(id, request);
    }

    @PatchMapping("/listings/{id}/activate")
    public SharingListingResponse activateListing(@PathVariable Long id) {
        return listingService.activateListing(id);
    }

    @PatchMapping("/listings/{id}/deactivate")
    public SharingListingResponse deactivateListing(@PathVariable Long id) {
        return listingService.deactivateListing(id);
    }

    @GetMapping("/listings/{id}")
    public SharingListingResponse getListingById(@PathVariable Long id) {
        return listingService.getListingById(id);
    }

    @GetMapping("/listings/equipment/{equipmentId}")
    public SharingListingResponse getListingByEquipmentId(@PathVariable Long equipmentId) {
        return listingService.getListingByEquipmentId(equipmentId);
    }

    @GetMapping({"/listings", "/discovery"})
    public Page<SharingListingDiscoveryResponse> discoverListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return listingService.discoverListings(institutionId, categoryId, keyword, pageable);
    }
}

