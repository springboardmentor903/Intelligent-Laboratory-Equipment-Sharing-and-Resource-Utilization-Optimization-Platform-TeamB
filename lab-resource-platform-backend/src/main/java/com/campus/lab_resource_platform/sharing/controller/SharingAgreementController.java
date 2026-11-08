package com.campus.lab_resource_platform.sharing.controller;

import com.campus.lab_resource_platform.sharing.dto.AccessGrantResponse;
import com.campus.lab_resource_platform.sharing.dto.AgreementUsageFeesResponse;
import com.campus.lab_resource_platform.sharing.dto.RevokeRequestDto;
import com.campus.lab_resource_platform.sharing.dto.SharingAgreementResponse;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.sharing.service.SharingAgreementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sharing")
public class SharingAgreementController {

    private final SharingAgreementService agreementService;
    private final FeeCalculationService feeCalculationService;

    public SharingAgreementController(
            SharingAgreementService agreementService,
            FeeCalculationService feeCalculationService) {
        this.agreementService = agreementService;
        this.feeCalculationService = feeCalculationService;
    }

    @GetMapping("/agreements")
    public Page<SharingAgreementResponse> getAgreements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return agreementService.getAgreements(pageable);
    }

    @GetMapping("/agreements/{id}")
    public SharingAgreementResponse getAgreementById(@PathVariable Long id) {
        return agreementService.getAgreementById(id);
    }

    @GetMapping("/agreements/{id}/grants")
    public List<AccessGrantResponse> getGrantsForAgreement(@PathVariable Long id) {
        return agreementService.getGrantsForAgreement(id);
    }

    @PostMapping("/agreements/{id}/revoke")
    public SharingAgreementResponse revokeAgreement(
            @PathVariable Long id,
            @Valid @RequestBody RevokeRequestDto dto) {
        return agreementService.revokeAgreement(id, dto);
    }

    @PostMapping("/grants/{id}/revoke")
    public AccessGrantResponse revokeGrant(
            @PathVariable Long id,
            @Valid @RequestBody RevokeRequestDto dto) {
        return agreementService.revokeGrant(id, dto);
    }

    @GetMapping("/agreements/{id}/usage-fees")
    public AgreementUsageFeesResponse getUsageFees(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return feeCalculationService.getAgreementUsageFees(id, startDate, endDate);
    }
}

