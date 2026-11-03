package com.campus.lab_resource_platform.utilization.controller;

import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.*;
import com.campus.lab_resource_platform.utilization.service.UtilizationAnalyticsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/analytics/utilization")
public class UtilizationAnalyticsController {

    private static final Set<User.Role> DISALLOWED_ROLES = Set.of(
            User.Role.RESEARCHER_STUDENT,
            User.Role.LAB_TECHNICIAN
    );

    private final UtilizationAnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;

    public UtilizationAnalyticsController(
            UtilizationAnalyticsService analyticsService,
            UserRepository userRepository,
            EquipmentRepository equipmentRepository,
            DepartmentRepository departmentRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * B. Utilization Summary
     */
    @GetMapping("/summary")
    public ResponseEntity<UtilizationAnalyticsSummaryDto> getSummary(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_7_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            UtilizationAnalyticsSummaryDto summary = analyticsService.getUtilizationSummary(
                    scope, targetScopeId, period, startDate, endDate);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * C. Benchmark comparison against preceding 28 days
     */
    @GetMapping("/benchmark")
    public ResponseEntity<UtilizationBenchmarkDto> getBenchmark(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_7_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            UtilizationBenchmarkDto benchmark = analyticsService.getBenchmark(
                    scope, targetScopeId, period, startDate, endDate);
            return ResponseEntity.ok(benchmark);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * D. Heatmap (168 slots: 7 days x 24 hours)
     */
    @GetMapping("/heatmap")
    public ResponseEntity<List<UtilizationHeatmapSlotDto>> getHeatmap(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_7_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            List<UtilizationHeatmapSlotDto> heatmap = analyticsService.getHeatmap(
                    scope, targetScopeId, period, startDate, endDate);
            return ResponseEntity.ok(heatmap);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * E. Peak Usage Analysis
     */
    @GetMapping("/peak-usage")
    public ResponseEntity<PeakUsageDto> getPeakUsage(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_7_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            PeakUsageDto peakUsage = analyticsService.getPeakUsage(
                    scope, targetScopeId, period, startDate, endDate);
            return ResponseEntity.ok(peakUsage);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * F. Idle Time Analysis
     */
    @GetMapping("/idle-time")
    public ResponseEntity<IdleTimeAnalysisDto> getIdleTime(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_7_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            IdleTimeAnalysisDto idleTime = analyticsService.getIdleTimeAnalysis(
                    scope, targetScopeId, period, startDate, endDate);
            return ResponseEntity.ok(idleTime);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * G. Idle Alerts (>= 90% idle in last 7 completed days)
     */
    @GetMapping("/idle-alerts")
    public ResponseEntity<List<IdleAlertDto>> getIdleAlerts(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            List<IdleAlertDto> alerts = analyticsService.getIdleAlerts(scope, targetScopeId);
            return ResponseEntity.ok(alerts);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * H. Demand Analysis
     */
    @GetMapping("/demand")
    public ResponseEntity<DemandAnalysisResponse> getDemandAnalysis(
            @RequestParam(defaultValue = "INSTITUTION") String scope,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(defaultValue = "LAST_7_DAYS") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "false") Boolean highDemandOnly,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeUser(authentication, scope, scopeId);
        Long targetScopeId = resolveScopeId(scope, scopeId, currentUser);

        try {
            DemandAnalysisResponse response = analyticsService.getDemandAnalysis(
                    scope, targetScopeId, period, startDate, endDate, highDemandOnly, page, size);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * Operating Hours APIs
     */
    @GetMapping("/equipment/{equipmentId}/operating-hours")
    public ResponseEntity<List<OperatingHoursDto>> getOperatingHours(@PathVariable Long equipmentId) {
        try {
            return ResponseEntity.ok(analyticsService.getOperatingHours(equipmentId));
        } catch (java.util.NoSuchElementException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        }
    }

    @PutMapping("/equipment/{equipmentId}/operating-hours")
    public ResponseEntity<List<OperatingHoursDto>> updateOperatingHours(
            @PathVariable Long equipmentId,
            @Valid @RequestBody List<OperatingHoursDto> dtos,
            Authentication authentication
    ) {
        User currentUser = validateAndAuthorizeStaff(authentication);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + equipmentId));

        validateEquipmentStaffAccess(currentUser, equipment);

        try {
            return ResponseEntity.ok(analyticsService.updateOperatingHours(equipmentId, dtos));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    // =========================================================================
    // RBAC & Scope Authorization Helpers
    // =========================================================================

    private User validateAndAuthorizeUser(Authentication authentication, String scope, Long scopeId) {
        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Authentication required");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("User account not found"));

        if (DISALLOWED_ROLES.contains(user.getRole())) {
            throw new ForbiddenException("Access denied: " + user.getRole() + " role cannot access utilization analytics");
        }

        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return user;
        }

        Long userColId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();

        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR || user.getRole() == User.Role.LAB_MANAGER) {
            if ("INSTITUTION".equalsIgnoreCase(scope) && scopeId != null && userColId != null && !userColId.equals(scopeId)) {
                throw new ForbiddenException("Access denied: You may only view analytics for your own institution");
            }
            if ("DEPARTMENT".equalsIgnoreCase(scope) && scopeId != null) {
                Department dept = departmentRepository.findById(scopeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + scopeId));
                Long deptColId = dept.getCollegeId() != null ? dept.getCollegeId() : dept.getInstitutionId();
                if (deptColId != null && userColId != null && !deptColId.equals(userColId)) {
                    throw new ForbiddenException("Access denied: Department belongs to a different institution");
                }
            }
            if ("EQUIPMENT".equalsIgnoreCase(scope) && scopeId != null) {
                Equipment eq = equipmentRepository.findById(scopeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + scopeId));
                Long eqColId = eq.getCollegeId() != null ? eq.getCollegeId() : eq.getInstitutionId();
                if (eqColId != null && userColId != null && !eqColId.equals(userColId)) {
                    throw new ForbiddenException("Access denied: Equipment belongs to a different institution");
                }
            }
        }

        if (user.getRole() == User.Role.DEPARTMENT_HEAD) {
            if ("INSTITUTION".equalsIgnoreCase(scope)) {
                throw new ForbiddenException("Access denied: Department Head cannot view institution-wide analytics");
            }
            if ("DEPARTMENT".equalsIgnoreCase(scope) && scopeId != null) {
                if (user.getDepartmentId() != null && !user.getDepartmentId().equals(scopeId)) {
                    throw new ForbiddenException("Access denied: You may only view analytics for your own department");
                }
            }
            if ("EQUIPMENT".equalsIgnoreCase(scope) && scopeId != null) {
                Equipment eq = equipmentRepository.findById(scopeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + scopeId));
                if (user.getDepartmentId() != null && eq.getDepartmentId() != null && !user.getDepartmentId().equals(eq.getDepartmentId())) {
                    throw new ForbiddenException("Access denied: Equipment is outside your department");
                }
            }
        }

        return user;
    }

    private Long resolveScopeId(String scope, Long requestedScopeId, User currentUser) {
        if (requestedScopeId != null) {
            return requestedScopeId;
        }
        if ("INSTITUTION".equalsIgnoreCase(scope)) {
            return currentUser.getCollegeId() != null ? currentUser.getCollegeId() : currentUser.getInstitutionId();
        }
        if ("DEPARTMENT".equalsIgnoreCase(scope)) {
            return currentUser.getDepartmentId();
        }
        return null;
    }

    private User validateAndAuthorizeStaff(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Authentication required");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("User account not found"));

        if (DISALLOWED_ROLES.contains(user.getRole())) {
            throw new ForbiddenException("Access denied: Only staff and managers may configure equipment operating hours");
        }
        return user;
    }

    private void validateEquipmentStaffAccess(User user, Equipment equipment) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        Long userColId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        Long eqColId = equipment.getCollegeId() != null ? equipment.getCollegeId() : equipment.getInstitutionId();
        if (userColId == null || eqColId == null || !userColId.equals(eqColId)) {
            throw new ForbiddenException("Access denied: Equipment is outside your institution");
        }
    }
}
