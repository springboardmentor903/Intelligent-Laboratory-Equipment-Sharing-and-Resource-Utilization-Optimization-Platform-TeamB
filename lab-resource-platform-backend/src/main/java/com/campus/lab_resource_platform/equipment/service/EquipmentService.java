
package com.campus.lab_resource_platform.equipment.service;

import com.campus.lab_resource_platform.equipment.dto.EquipmentRequest;
import com.campus.lab_resource_platform.equipment.dto.EquipmentResponse;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentCategory;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentCategoryRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.campus.lab_resource_platform.equipment.dto.EquipmentStatusRequest;
import com.campus.lab_resource_platform.equipment.entity.Tag;
import jakarta.persistence.criteria.Join;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import java.util.List;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentCategoryRepository categoryRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final UserRepository userRepository;

    public EquipmentService(
        EquipmentRepository equipmentRepository,
        EquipmentCategoryRepository categoryRepository,
        CalibrationRecordRepository calibrationRecordRepository,
        UserRepository userRepository) {

    this.equipmentRepository = equipmentRepository;
    this.categoryRepository = categoryRepository;
    this.calibrationRecordRepository = calibrationRecordRepository;
    this.userRepository = userRepository;
}

    @Transactional(readOnly = true)
    public Page<EquipmentResponse> getEquipment(
        int page,
        int size,
        EquipmentStatus status,
        Long categoryId,
        Long tagId,
        Long departmentId,
        String keyword) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100"
            );
        }

        Specification<Equipment> spec = (root, query, cb) -> cb.conjunction();

        if (status != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }

        if (categoryId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("category").get("id"), categoryId));
        }

        if (tagId != null) {
            spec = spec.and((root, query, cb) -> {
                Join<Equipment, Tag> tagJoin = root.join("tags");
                query.distinct(true);
                return cb.equal(tagJoin.get("id"), tagId);
            });
        }

        if (departmentId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("departmentId"), departmentId));
        }

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";

            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("name")), pattern),
                            cb.like(cb.lower(root.get("assetTag")), pattern),
                            cb.like(cb.lower(root.get("description")), pattern)
                    ));
        }

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "id"));

        return equipmentRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentById(Long id) {
        return toResponse(findEquipment(id));
    }

   @Transactional
public EquipmentResponse createEquipment(EquipmentRequest request) {
    authorizeEquipmentManagement(request.getInstitutionId());
    validateRequest(request, null);

    Equipment equipment = new Equipment();
    applyRequest(equipment, request);

    return toResponse(equipmentRepository.save(equipment));
}

    @Transactional
public EquipmentResponse updateEquipment(
        Long id,
        EquipmentRequest request) {

    Equipment equipment = findEquipment(id);

    authorizeEquipmentManagement(equipment.getInstitutionId());

    User user = getAuthenticatedUser();

    if (!java.util.Objects.equals(
            equipment.getInstitutionId(),
            request.getInstitutionId())
            && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot move equipment to another institution"
        );
    }

    validateRequest(request, id);
    applyRequest(equipment, request);

    return toResponse(equipmentRepository.save(equipment));
}

    private void validateRequest(EquipmentRequest request, Long currentId) {
        equipmentRepository.findByAssetTagIgnoreCase(request.getAssetTag().trim())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Asset tag already exists"
                    );
                });

        if (request.getPurchaseDate() != null
                && request.getPurchaseDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase date cannot be in the future"
            );
        }

        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Category does not exist"
                    ));
        }
    }

    private void applyRequest(Equipment equipment, EquipmentRequest request) {
        equipment.setName(request.getName().trim());
        equipment.setAssetTag(request.getAssetTag().trim());
        equipment.setDescription(request.getDescription());
        equipment.setPurchaseCost(request.getPurchaseCost());
        equipment.setHourlyCost(request.getHourlyCost());
        equipment.setPurchaseDate(request.getPurchaseDate());
        equipment.setDepartmentId(request.getDepartmentId());
        equipment.setInstitutionId(request.getInstitutionId());

        if (request.getCategoryId() == null) {
            equipment.setCategory(null);
        } else {
            EquipmentCategory category = categoryRepository
                    .findById(request.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Category does not exist"
                    ));
            equipment.setCategory(category);
        }
    }


    private User getAuthenticatedUser() {
    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null
            || !authentication.isAuthenticated()
            || authentication.getName() == null
            || authentication.getName().equals("anonymousUser")) {
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required"
        );
    }


    User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user was not found"
            ));

    if (!user.isActive()) {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Inactive users cannot manage equipment"
        );
    }

    return user;
}

private void authorizeEquipmentManagement(Long institutionId) {
    User user = getAuthenticatedUser();
    User.Role role = user.getRole();

    if (role != User.Role.LAB_MANAGER
            && role != User.Role.INSTITUTION_ADMINISTRATOR
            && role != User.Role.SYSTEM_ADMINISTRATOR) {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to manage equipment"
        );
    }

    if (role == User.Role.SYSTEM_ADMINISTRATOR) {
        return;
    }

    if (user.getInstitutionId() == null
            || institutionId == null
            || !user.getInstitutionId().equals(institutionId)) {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You can manage equipment only within your institution"
        );
    }
}

    public void authorizeEquipmentManagementFor(Long equipmentId) {
    Equipment equipment = findEquipment(equipmentId);
    authorizeEquipmentManagement(equipment.getInstitutionId());
}

    private Equipment findEquipment(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Equipment not found: " + id
                ));
    }

    
private EquipmentResponse toResponse(Equipment equipment) {
    EquipmentResponse response = new EquipmentResponse();

    response.setId(equipment.getId());
    response.setName(equipment.getName());
    response.setAssetTag(equipment.getAssetTag());
    response.setDescription(equipment.getDescription());
    response.setStatus(equipment.getStatus());
    response.setPurchaseCost(equipment.getPurchaseCost());
    response.setHourlyCost(equipment.getHourlyCost());
    response.setPurchaseDate(equipment.getPurchaseDate());
    response.setDepartmentId(equipment.getDepartmentId());
    response.setInstitutionId(equipment.getInstitutionId());

    if (equipment.getCategory() != null) {
        response.setCategoryId(equipment.getCategory().getId());
        response.setCategoryName(equipment.getCategory().getName());
    }

    response.setTags(
            equipment.getTags().stream()
                    .map(tag -> new EquipmentResponse.TagSummary(
                            tag.getId(), tag.getName()))
                    .toList()
    );

    List<CalibrationRecord> records =
            calibrationRecordRepository
                    .findByEquipmentIdOrderByCalibrationDateDesc(
                            equipment.getId());

    if (records.isEmpty()) {
        response.setCalibrationState("NOT_RECORDED");
    } else {
        CalibrationRecord latest = records.get(0);

        response.setLastCalibrationDate(latest.getCalibrationDate());
        response.setNextCalibrationDate(latest.getNextCalibrationDate());

        LocalDate nextDate = latest.getNextCalibrationDate();
        LocalDate today = LocalDate.now();

        if (nextDate == null) {
            response.setCalibrationState("NOT_RECORDED");
        } else if (nextDate.isBefore(today)) {
            response.setCalibrationState("OVERDUE");
        } else if (!nextDate.isAfter(today.plusDays(30))) {
            response.setCalibrationState("DUE_SOON");
        } else {
            response.setCalibrationState("UP_TO_DATE");
        }
    }

    return response;
}



    
@Transactional
public EquipmentResponse updateEquipmentStatus(
        Long id,
        EquipmentStatusRequest request) {

    Equipment equipment = findEquipment(id);

    authorizeEquipmentManagement(equipment.getInstitutionId());

    EquipmentStatus newStatus = request.getStatus();

    if (newStatus == EquipmentStatus.BOOKED) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "BOOKED status can only be set by the booking workflow"
        );
    }

    EquipmentStatus currentStatus = equipment.getStatus();

if (currentStatus == EquipmentStatus.BOOKED) {
    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Booked equipment status must be changed through the booking workflow"
    );
}

if (currentStatus == EquipmentStatus.RETIRED) {
    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Retired equipment cannot be reactivated"
    );
}

boolean validTransition = switch (currentStatus) {
    case AVAILABLE ->
            newStatus == EquipmentStatus.UNDER_MAINTENANCE
                    || newStatus == EquipmentStatus.CALIBRATION
                    || newStatus == EquipmentStatus.OUT_OF_SERVICE
                    || newStatus == EquipmentStatus.RETIRED;

    case UNDER_MAINTENANCE ->
            newStatus == EquipmentStatus.AVAILABLE
                    || newStatus == EquipmentStatus.CALIBRATION
                    || newStatus == EquipmentStatus.OUT_OF_SERVICE
                    || newStatus == EquipmentStatus.RETIRED;

    case CALIBRATION ->
            newStatus == EquipmentStatus.AVAILABLE
                    || newStatus == EquipmentStatus.UNDER_MAINTENANCE
                    || newStatus == EquipmentStatus.OUT_OF_SERVICE
                    || newStatus == EquipmentStatus.RETIRED;

    case OUT_OF_SERVICE ->
            newStatus == EquipmentStatus.AVAILABLE
                    || newStatus == EquipmentStatus.UNDER_MAINTENANCE
                    || newStatus == EquipmentStatus.CALIBRATION
                    || newStatus == EquipmentStatus.RETIRED;

    case BOOKED, RETIRED -> false;
};

if (!validTransition) {
    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid equipment status transition: "
                    + currentStatus + " -> " + newStatus
    );
}

equipment.setStatus(newStatus);

    return toResponse(equipmentRepository.save(equipment));
}

}
