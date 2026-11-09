package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.booking.dto.AvailabilitySlotResponse;
import com.campus.lab_resource_platform.booking.dto.SlotRecommendationResponse;
import com.campus.lab_resource_platform.booking.service.BookingAutoScheduler;
import com.campus.lab_resource_platform.booking.service.BookingOptimizationService;
import com.campus.lab_resource_platform.booking.service.BookingService;
import com.campus.lab_resource_platform.equipment.dto.EquipmentRequest;
import com.campus.lab_resource_platform.equipment.dto.EquipmentResponse;
import com.campus.lab_resource_platform.equipment.dto.EquipmentStatusRequest;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private static final Set<User.Role> COLLEGE_STAFF_ROLES = Set.of(
            User.Role.INSTITUTION_ADMINISTRATOR,
            User.Role.DEPARTMENT_HEAD,
            User.Role.LAB_MANAGER,
            User.Role.LAB_TECHNICIAN
    );

    private final BookingService bookingService;
    private final EquipmentService equipmentService;
    private final UserRepository userRepository;
    private final BookingOptimizationService bookingOptimizationService;
    private final BookingAutoScheduler bookingAutoScheduler;

    public EquipmentController(
            BookingService bookingService,
            EquipmentService equipmentService,
            UserRepository userRepository,
            BookingOptimizationService bookingOptimizationService,
            @Lazy BookingAutoScheduler bookingAutoScheduler) {
        this.bookingService = bookingService;
        this.equipmentService = equipmentService;
        this.userRepository = userRepository;
        this.bookingOptimizationService = bookingOptimizationService;
        this.bookingAutoScheduler = bookingAutoScheduler;
    }

    @GetMapping
    public ResponseEntity<?> getEquipment(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            Authentication authentication) {
        bookingAutoScheduler.processBookingLifecycle();
        User user = getAuthenticatedUser(authentication);
        Long staffCollegeId = getStaffEnforcedCollegeId(user);
        Long targetCollegeId = staffCollegeId != null ? staffCollegeId : collegeId;

        boolean paginatedQuery = page != null || size != null || status != null
                || categoryId != null || tagId != null || departmentId != null
                || (keyword != null && !keyword.isBlank());
        if (paginatedQuery) {
            Page<EquipmentResponse> result = equipmentService.getEquipment(
                    page != null ? page : 0,
                    size != null ? size : 10,
                    status,
                    categoryId,
                    tagId,
                    departmentId,
                    keyword,
                    targetCollegeId
            );
            return ResponseEntity.ok(result);
        }

        if (user != null && COLLEGE_STAFF_ROLES.contains(user.getRole()) && targetCollegeId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Equipment> equipment = equipmentService.getAllEquipment().stream()
                .filter(Equipment::isActive)
                .filter(item -> targetCollegeId == null
                        || targetCollegeId.equals(item.getCollegeId())
                        || targetCollegeId.equals(item.getInstitutionId()))
                .filter(item -> category == null || category.isBlank()
                        || categoryMatches(item, category))
                .filter(item -> search == null || search.isBlank()
                        || matchesSearch(item, search))
                .toList();
        return ResponseEntity.ok(equipment);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentResponse> getEquipmentById(
            @PathVariable Long id,
            Authentication authentication) {
        bookingAutoScheduler.processBookingLifecycle();
        Equipment equipment = equipmentService.getEquipmentById(id);
        enforceCollegeAccess(equipment, getAuthenticatedUser(authentication));
        return ResponseEntity.ok(equipmentService.getEquipmentResponseById(id));
    }

    @PostMapping
    public ResponseEntity<EquipmentResponse> createEquipment(
            @RequestBody EquipmentRequest request,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        applyStaffInstitution(request, user);
        if (request.getAssetTag() == null || request.getAssetTag().isBlank()) {
            request.setAssetTag("EQ-" + java.util.UUID.randomUUID().toString().substring(0, 12));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(equipmentService.createEquipment(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EquipmentResponse> updateEquipment(
            @PathVariable Long id,
            @RequestBody EquipmentRequest request,
            Authentication authentication) {
        Equipment existing = equipmentService.getEquipmentById(id);
        User user = getAuthenticatedUser(authentication);
        enforceCollegeAccess(existing, user);
        if (request.getAssetTag() == null || request.getAssetTag().isBlank()) {
            request.setAssetTag(existing.getAssetTag());
        }
        if (request.getInstitutionId() == null) {
            request.setInstitutionId(existing.getInstitutionId());
        }
        if (request.getCollegeId() == null) {
            request.setCollegeId(existing.getCollegeId());
        }
        applyStaffInstitution(request, user);
        return ResponseEntity.ok(equipmentService.updateEquipment(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EquipmentResponse> updateEquipmentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Equipment equipment = equipmentService.getEquipmentById(id);
        enforceCollegeAccess(equipment, getAuthenticatedUser(authentication));
        String status = body == null ? null : body.get("status");
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        try {
            EquipmentStatusRequest request = new EquipmentStatusRequest();
            request.setStatus(EquipmentStatus.valueOf(status.trim().toUpperCase()));
            return ResponseEntity.ok(equipmentService.updateEquipmentStatus(id, request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status, e);
        }
    }

    @PatchMapping("/{id}/shareable")
    public EquipmentResponse updateEquipmentShareable(
            @PathVariable Long id,
            @RequestParam boolean shareable) {
        return equipmentService.updateEquipmentShareable(id, shareable);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable Long id,
            Authentication authentication) {
        Equipment equipment = equipmentService.getEquipmentById(id);
        enforceCollegeAccess(equipment, getAuthenticatedUser(authentication));
        equipmentService.deleteEquipment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<List<AvailabilitySlotResponse>> getEquipmentAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }
        return ResponseEntity.ok(bookingService.getEquipmentAvailability(id, startDate, endDate));
    }

    @GetMapping("/{id}/recommend-slots")
    public ResponseEntity<SlotRecommendationResponse> getRecommendedSlots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "120") int durationMinutes) {
        return ResponseEntity.ok(bookingOptimizationService.recommendSlots(id, date, durationMinutes));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private Long getStaffEnforcedCollegeId(User user) {
        if (user != null && COLLEGE_STAFF_ROLES.contains(user.getRole())) {
            return user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        }
        return null;
    }

    private void enforceCollegeAccess(Equipment equipment, User user) {
        Long staffCollegeId = getStaffEnforcedCollegeId(user);
        if (staffCollegeId != null) {
            Long equipmentCollegeId = equipment.getCollegeId() != null
                    ? equipment.getCollegeId()
                    : equipment.getInstitutionId();
            if (!staffCollegeId.equals(equipmentCollegeId)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access denied. You can only manage equipment belonging to your college."
                );
            }
        }
    }

    private void applyStaffInstitution(EquipmentRequest request, User user) {
        Long staffCollegeId = getStaffEnforcedCollegeId(user);
        if (staffCollegeId != null) {
            request.setCollegeId(staffCollegeId);
            request.setInstitutionId(staffCollegeId);
        }
    }

    private boolean categoryMatches(Equipment equipment, String requestedCategory) {
        String category = equipment.getCategory() != null
                ? equipment.getCategory().getName()
                : equipment.getLegacyCategory();
        return category != null && category.equalsIgnoreCase(requestedCategory.trim());
    }

    private boolean matchesSearch(Equipment equipment, String search) {
        String query = search.trim().toLowerCase();
        return contains(equipment.getName(), query)
                || contains(equipment.getModel(), query)
                || contains(equipment.getManufacturer(), query)
                || contains(equipment.getLegacyCategory(), query)
                || contains(equipment.getSpecifications(), query)
                || contains(equipment.getLegacyTags(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }
}
