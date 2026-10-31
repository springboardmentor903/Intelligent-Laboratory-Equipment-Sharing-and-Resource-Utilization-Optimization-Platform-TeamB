package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.booking.dto.AvailabilitySlotResponse;
import com.campus.lab_resource_platform.booking.dto.SlotRecommendationResponse;
import com.campus.lab_resource_platform.booking.service.BookingOptimizationService;
import com.campus.lab_resource_platform.booking.service.BookingService;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    public EquipmentController(
            BookingService bookingService,
            EquipmentService equipmentService,
            UserRepository userRepository,
            BookingOptimizationService bookingOptimizationService) {
        this.bookingService = bookingService;
        this.equipmentService = equipmentService;
        this.userRepository = userRepository;
        this.bookingOptimizationService = bookingOptimizationService;
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

    @GetMapping
    public ResponseEntity<List<Equipment>> getAllEquipment(
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            Authentication authentication) {

        User currentUser = getAuthenticatedUser(authentication);
        final Long targetCollegeId;

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = getStaffEnforcedCollegeId(currentUser);
            if (staffCollegeId == null) {
                return ResponseEntity.ok(List.of());
            }
            targetCollegeId = staffCollegeId;
        } else {
            targetCollegeId = collegeId;
        }

        List<Equipment> list = equipmentService.getAllEquipment();
        if (targetCollegeId != null) {
            list = list.stream()
                    .filter(e -> targetCollegeId.equals(e.getCollegeId()) || targetCollegeId.equals(e.getInstitutionId()))
                    .toList();
        }
        if (category != null && !category.isBlank()) {
            list = list.stream()
                    .filter(e -> e.getCategory() != null && e.getCategory().equalsIgnoreCase(category.trim()))
                    .toList();
        }
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            list = list.stream()
                    .filter(e -> (e.getName() != null && e.getName().toLowerCase().contains(q))
                            || (e.getModel() != null && e.getModel().toLowerCase().contains(q))
                            || (e.getManufacturer() != null && e.getManufacturer().toLowerCase().contains(q))
                            || (e.getCategory() != null && e.getCategory().toLowerCase().contains(q))
                            || (e.getSpecifications() != null && e.getSpecifications().toLowerCase().contains(q))
                            || (e.getTags() != null && e.getTags().toLowerCase().contains(q)))
                    .toList();
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Equipment> getEquipmentById(
            @PathVariable Long id,
            Authentication authentication) {
        Equipment equipment = equipmentService.getEquipmentById(id);
        User currentUser = getAuthenticatedUser(authentication);

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = getStaffEnforcedCollegeId(currentUser);
            Long eqCollegeId = equipment.getCollegeId() != null ? equipment.getCollegeId() : equipment.getInstitutionId();
            if (staffCollegeId == null || !staffCollegeId.equals(eqCollegeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. You can only view equipment belonging to your college.");
            }
        }

        return ResponseEntity.ok(equipment);
    }

    @PostMapping
    public ResponseEntity<Equipment> createEquipment(
            @RequestBody Equipment equipment,
            Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = getStaffEnforcedCollegeId(currentUser);
            if (staffCollegeId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is not assigned to any college. Cannot register equipment.");
            }
            equipment.setCollegeId(staffCollegeId);
            equipment.setInstitutionId(staffCollegeId);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.createEquipment(equipment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> updateEquipment(
            @PathVariable Long id,
            @RequestBody Equipment equipment,
            Authentication authentication) {
        Equipment existing = equipmentService.getEquipmentById(id);
        User currentUser = getAuthenticatedUser(authentication);

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = getStaffEnforcedCollegeId(currentUser);
            Long eqCollegeId = existing.getCollegeId() != null ? existing.getCollegeId() : existing.getInstitutionId();
            if (staffCollegeId == null || !staffCollegeId.equals(eqCollegeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. You can only update equipment belonging to your college.");
            }
            equipment.setCollegeId(staffCollegeId);
            equipment.setInstitutionId(staffCollegeId);
        }

        return ResponseEntity.ok(equipmentService.updateEquipment(id, equipment));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Equipment> updateEquipmentStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication) {
        String statusStr = body != null ? body.get("status") : null;
        if (statusStr == null || statusStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        Equipment existing = equipmentService.getEquipmentById(id);
        User currentUser = getAuthenticatedUser(authentication);

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = getStaffEnforcedCollegeId(currentUser);
            Long eqCollegeId = existing.getCollegeId() != null ? existing.getCollegeId() : existing.getInstitutionId();
            if (staffCollegeId == null || !staffCollegeId.equals(eqCollegeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. You can only update equipment belonging to your college.");
            }
        }

        try {
            existing.setStatus(com.campus.lab_resource_platform.equipment.entity.EquipmentStatus.valueOf(statusStr.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + statusStr);
        }
        return ResponseEntity.ok(equipmentService.updateEquipment(id, existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable Long id,
            Authentication authentication) {
        Equipment existing = equipmentService.getEquipmentById(id);
        User currentUser = getAuthenticatedUser(authentication);

        if (currentUser != null && COLLEGE_STAFF_ROLES.contains(currentUser.getRole())) {
            Long staffCollegeId = getStaffEnforcedCollegeId(currentUser);
            Long eqCollegeId = existing.getCollegeId() != null ? existing.getCollegeId() : existing.getInstitutionId();
            if (staffCollegeId == null || !staffCollegeId.equals(eqCollegeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. You can only delete equipment belonging to your college.");
            }
        }

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

        List<AvailabilitySlotResponse> slots = bookingService.getEquipmentAvailability(id, startDate, endDate);
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/{id}/recommend-slots")
    public ResponseEntity<SlotRecommendationResponse> getRecommendedSlots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "120") int durationMinutes) {
        SlotRecommendationResponse recommendations = bookingOptimizationService.recommendSlots(id, date, durationMinutes);
        return ResponseEntity.ok(recommendations);
    }
}
