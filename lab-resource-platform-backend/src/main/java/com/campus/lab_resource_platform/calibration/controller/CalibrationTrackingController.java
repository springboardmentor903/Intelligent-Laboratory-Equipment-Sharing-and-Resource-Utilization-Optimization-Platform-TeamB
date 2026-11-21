package com.campus.lab_resource_platform.calibration.controller;

import com.campus.lab_resource_platform.calibration.dto.CalibrationOverviewDto;
import com.campus.lab_resource_platform.calibration.dto.CalibrationReminderDto;
import com.campus.lab_resource_platform.calibration.dto.CalibrationState;
import com.campus.lab_resource_platform.calibration.service.CalibrationTrackingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calibration")
public class CalibrationTrackingController {

    private final CalibrationTrackingService calibrationService;

    public CalibrationTrackingController(CalibrationTrackingService calibrationService) {
        this.calibrationService = calibrationService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Page<CalibrationOverviewDto>> getCalibrationOverview(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) CalibrationState calibrationState,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "daysUntilDue") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Authentication authentication
    ) {
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(calibrationService.getCalibrationOverview(departmentId, calibrationState, keyword, authentication.getName(), pageable));
    }

    @GetMapping("/reminders")
    public ResponseEntity<List<CalibrationReminderDto>> getCalibrationReminders(
            Authentication authentication
    ) {
        return ResponseEntity.ok(calibrationService.getReminders(authentication.getName()));
    }
}
