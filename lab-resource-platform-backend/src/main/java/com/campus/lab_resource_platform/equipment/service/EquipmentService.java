package com.campus.lab_resource_platform.equipment.service;

import com.campus.lab_resource_platform.equipment.dto.EquipmentRequest;
import com.campus.lab_resource_platform.equipment.dto.EquipmentResponse;
import com.campus.lab_resource_platform.equipment.dto.EquipmentStatusRequest;
import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentCategory;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.entity.Tag;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentCategoryRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import com.campus.lab_resource_platform.sharing.repository.SharingListingRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class EquipmentService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final EquipmentRepository equipmentRepository;
    private final EquipmentCategoryRepository categoryRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final UserRepository userRepository;
    private final SharingListingRepository sharingListingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            EquipmentCategoryRepository categoryRepository,
            CalibrationRecordRepository calibrationRecordRepository,
            UserRepository userRepository,
            SharingListingRepository sharingListingRepository) {
        this.equipmentRepository = equipmentRepository;
        this.categoryRepository = categoryRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.userRepository = userRepository;
        this.sharingListingRepository = sharingListingRepository;
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
        return getEquipment(page, size, status, categoryId, tagId, departmentId, keyword, null);
    }

    @Transactional(readOnly = true)
    public Page<EquipmentResponse> getEquipment(
            int page,
            int size,
            EquipmentStatus status,
            Long categoryId,
            Long tagId,
            Long departmentId,
            String keyword,
            Long collegeId) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100"
            );
        }

        Specification<Equipment> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (tagId != null) {
            specification = specification.and((root, query, cb) -> {
                Join<Equipment, Tag> tagJoin = root.join("tagEntities");
                query.distinct(true);
                return cb.equal(tagJoin.get("id"), tagId);
            });
        }
        if (departmentId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("departmentId"), departmentId));
        }
        if (collegeId != null) {
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("collegeId"), collegeId),
                    cb.equal(root.get("institutionId"), collegeId)
            ));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("assetTag")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("legacyCategory")), pattern),
                    cb.like(cb.lower(root.get("tags")), pattern)
            ));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return equipmentRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Equipment getEquipmentById(Long id) {
        return findEquipment(id);
    }

    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentResponseById(Long id) {
        return toResponse(findEquipment(id));
    }

    @Transactional
    public EquipmentResponse createEquipment(EquipmentRequest request) {
        authorizeEquipmentManagement(request.getInstitutionId());
        validateRequest(request, null);
        Equipment equipment = new Equipment();
        applyRequest(equipment, request);
        Equipment saved = equipmentRepository.saveAndFlush(equipment);
        if (Boolean.TRUE.equals(saved.getIsShareable())) {
            autoProvisionSharingListing(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public Equipment createEquipment(Equipment equipment) {
        User user = getAuthenticatedUser();
        Long institutionId = equipment.getInstitutionId();
        authorizeEquipmentManagement(institutionId);
        enforceCollegeScope(user, equipment);
        equipment.setId(null);
        equipment.setRequiresApproval(true);
        Equipment saved = equipmentRepository.saveAndFlush(equipment);
        if (Boolean.TRUE.equals(saved.getIsShareable())) {
            autoProvisionSharingListing(saved);
        }
        return saved;
    }

    @Transactional
    public EquipmentResponse updateEquipment(Long id, EquipmentRequest request) {
        Equipment equipment = findEquipment(id);
        authorizeEquipmentManagement(equipment.getInstitutionId());
        User user = getAuthenticatedUser();
        if (!Objects.equals(equipment.getInstitutionId(), request.getInstitutionId())
                && user.getRole() != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot move equipment to another institution");
        }

        validateRequest(request, id);
        applyRequest(equipment, request);
        return toResponse(equipmentRepository.save(equipment));
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment updated) {
        Equipment existing = findEquipment(id);
        User user = getAuthenticatedUser();
        authorizeEquipmentManagement(existing.getInstitutionId());
        enforceCollegeScope(user, existing);

        existing.setName(updated.getName());
        existing.setAssetTag(updated.getAssetTag());
        existing.setDescription(updated.getDescription());
        existing.setLegacyCategory(updated.getLegacyCategory());
        existing.setModel(updated.getModel());
        existing.setManufacturer(updated.getManufacturer());
        existing.setSerialNumber(updated.getSerialNumber());
        existing.setLocation(updated.getLocation());
        existing.setSpecifications(updated.getSpecifications());
        existing.setDocumentationUrl(updated.getDocumentationUrl());
        existing.setLegacyTags(updated.getLegacyTags());
        existing.setStatus(updated.getStatus());
        existing.setInstitutionId(updated.getInstitutionId());
        existing.setCollegeId(updated.getCollegeId());
        existing.setDepartmentId(updated.getDepartmentId());
        existing.setCalibrationDate(updated.getCalibrationDate());
        existing.setCertificationInfo(updated.getCertificationInfo());
        existing.setMaxBookingHours(updated.getMaxBookingHours());
        existing.setActive(updated.isActive());
        existing.setRequiresApproval(true);

        return equipmentRepository.save(existing);
    }

    @Transactional
    public void deleteEquipment(Long id) {
        Equipment equipment = findEquipment(id);
        authorizeEquipmentManagement(equipment.getInstitutionId());
        equipment.setActive(false);
        equipmentRepository.save(equipment);
    }

    @Transactional
    public EquipmentResponse updateEquipmentStatus(Long id, EquipmentStatusRequest request) {
        Equipment equipment = findEquipment(id);
        authorizeEquipmentManagement(equipment.getInstitutionId());
        EquipmentStatus next = request.getStatus();
        EquipmentStatus current = equipment.getStatus();

        if (next == EquipmentStatus.BOOKED || next == EquipmentStatus.IN_USE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking status can only be set by the booking workflow");
        }
        if (current == EquipmentStatus.BOOKED || current == EquipmentStatus.IN_USE
                || current == EquipmentStatus.RETIRED || current == EquipmentStatus.DECOMMISSIONED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment status cannot be changed from " + current);
        }

        boolean validTransition = switch (current) {
            case AVAILABLE -> next == EquipmentStatus.UNDER_MAINTENANCE
                    || next == EquipmentStatus.CALIBRATION
                    || next == EquipmentStatus.OUT_OF_SERVICE
                    || next == EquipmentStatus.RETIRED
                    || next == EquipmentStatus.DECOMMISSIONED;
            case UNDER_MAINTENANCE -> next == EquipmentStatus.AVAILABLE
                    || next == EquipmentStatus.CALIBRATION
                    || next == EquipmentStatus.OUT_OF_SERVICE
                    || next == EquipmentStatus.RETIRED
                    || next == EquipmentStatus.DECOMMISSIONED;
            case CALIBRATION -> next == EquipmentStatus.AVAILABLE
                    || next == EquipmentStatus.UNDER_MAINTENANCE
                    || next == EquipmentStatus.OUT_OF_SERVICE
                    || next == EquipmentStatus.RETIRED
                    || next == EquipmentStatus.DECOMMISSIONED;
            case OUT_OF_SERVICE -> next == EquipmentStatus.AVAILABLE
                    || next == EquipmentStatus.UNDER_MAINTENANCE
                    || next == EquipmentStatus.CALIBRATION
                    || next == EquipmentStatus.RETIRED
                    || next == EquipmentStatus.DECOMMISSIONED;
            case BOOKED, IN_USE, RETIRED, DECOMMISSIONED -> false;
        };
        if (!validTransition) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid equipment status transition: " + current + " -> " + next
            );
        }

        equipment.setStatus(next);
        return toResponse(equipmentRepository.save(equipment));
    }

    @Transactional
    public EquipmentResponse updateEquipmentShareable(Long id, boolean shareable) {
        Equipment equipment = findEquipment(id);
        authorizeEquipmentManagement(equipment.getInstitutionId());
        equipment.setIsShareable(shareable);
        Equipment saved = equipmentRepository.save(equipment);
        if (shareable) {
            autoProvisionSharingListing(saved);
        } else {
            sharingListingRepository.findByEquipmentId(id).ifPresent(l -> {
                l.setActive(false);
                sharingListingRepository.save(l);
            });
        }
        return toResponse(saved);
    }

    public void authorizeEquipmentManagementFor(Long equipmentId) {
        Equipment equipment = findEquipment(equipmentId);
        authorizeEquipmentManagement(equipment.getInstitutionId());
    }

    private void validateRequest(EquipmentRequest request, Long currentId) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment name is required");
        }
        if (request.getAssetTag() == null || request.getAssetTag().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset tag is required");
        }
        equipmentRepository.findByAssetTagIgnoreCase(request.getAssetTag().trim())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset tag already exists");
                });
        if (request.getPurchaseDate() != null && request.getPurchaseDate().isAfter(LocalDate.now(IST_ZONE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase date cannot be in the future");
        }
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category does not exist"));
        }
        if (request.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution ID is required");
        }
    }

    private void applyRequest(Equipment equipment, EquipmentRequest request) {
        equipment.setName(request.getName().trim());
        equipment.setAssetTag(request.getAssetTag().trim());
        equipment.setDescription(request.getDescription());
        equipment.setLegacyCategory(request.getCategory());
        equipment.setModel(request.getModel());
        equipment.setManufacturer(request.getManufacturer());
        equipment.setSerialNumber(request.getSerialNumber());
        equipment.setLocation(request.getLocation());
        equipment.setSpecifications(request.getSpecifications());
        equipment.setDocumentationUrl(request.getDocumentationUrl());
        equipment.setLegacyTags(request.getTags());
        if (request.getStatus() != null) {
            equipment.setStatus(request.getStatus());
        }
        equipment.setPurchaseCost(request.getPurchaseCost());
        equipment.setHourlyCost(request.getHourlyCost());
        equipment.setPurchaseDate(request.getPurchaseDate());
        equipment.setDepartmentId(request.getDepartmentId());
        equipment.setInstitutionId(request.getInstitutionId());
        equipment.setCollegeId(request.getCollegeId());
        equipment.setCalibrationDate(request.getCalibrationDate());
        equipment.setCertificationInfo(request.getCertificationInfo());
        if (request.getMaxBookingHours() != null) {
            equipment.setMaxBookingHours(request.getMaxBookingHours());
        }
        if (request.getActive() != null) {
            equipment.setActive(request.getActive());
        }
        if (request.getIsShareable() != null) {
            equipment.setIsShareable(request.getIsShareable());
        }
        if (request.getRequiresApproval() != null) {
            equipment.setRequiresApproval(request.getRequiresApproval());
        }
        if (request.getOperatingStartTime() != null) {
            equipment.setOperatingStartTime(request.getOperatingStartTime());
        }
        if (request.getOperatingEndTime() != null) {
            equipment.setOperatingEndTime(request.getOperatingEndTime());
        }

        EquipmentCategory category = request.getCategoryId() == null
                ? null
                : categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category does not exist"));
        equipment.setCategory(category);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().equals("anonymousUser")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot manage equipment");
        }
        return user;
    }

    private void authorizeEquipmentManagement(Long institutionId) {
        User user = getAuthenticatedUser();
        User.Role role = user.getRole();
        if (role != User.Role.LAB_MANAGER
                && role != User.Role.INSTITUTION_ADMINISTRATOR
                && role != User.Role.DEPARTMENT_HEAD
                && role != User.Role.LAB_TECHNICIAN
                && role != User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to manage equipment");
        }
        // Institution Administrators, Department Heads, and System Admins can onboard partner equipment for cross-institution sharing
        boolean isLeadAdmin = role == User.Role.SYSTEM_ADMINISTRATOR
                || role == User.Role.INSTITUTION_ADMINISTRATOR
                || role == User.Role.DEPARTMENT_HEAD;
        if (!isLeadAdmin && (user.getInstitutionId() == null || institutionId == null
                || !user.getInstitutionId().equals(institutionId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can manage equipment only within your institution");
        }
    }

    private void enforceCollegeScope(User user, Equipment equipment) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        if (equipment.getCollegeId() != null) {
            if (equipment.getInstitutionId() == null) {
                equipment.setInstitutionId(equipment.getCollegeId());
            }
            return;
        }
        Long userCollegeId = user.getCollegeId() != null ? user.getCollegeId() : user.getInstitutionId();
        if (userCollegeId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is not assigned to a college");
        }
        equipment.setCollegeId(userCollegeId);
        equipment.setInstitutionId(userCollegeId);
    }

    private void autoProvisionSharingListing(Equipment equipment) {
        try {
            if (!sharingListingRepository.existsByEquipmentId(equipment.getId())) {
                SharingListing listing = new SharingListing();
                listing.setEquipment(equipment);
                Long ownerInst = equipment.getCollegeId() != null 
                        ? equipment.getCollegeId() 
                        : (equipment.getInstitutionId() != null ? equipment.getInstitutionId() : 1L);
                listing.setOwnerInstitutionId(ownerInst);
                listing.setFeePerHour(equipment.getHourlyCost() != null ? equipment.getHourlyCost() : new BigDecimal("250.00"));
                listing.setExternalSharePercentage(new BigDecimal("100.00"));
                listing.setMinNoticeHours(0);
                listing.setMaxBookingDurationHours(equipment.getMaxBookingHours() > 0 ? equipment.getMaxBookingHours() : 8);
                listing.setSharingTerms("Available for inter-institution research and laboratory sharing.");
                listing.setActive(true);
                sharingListingRepository.saveAndFlush(listing);
            }
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(EquipmentService.class).warn("Failed to auto-provision sharing listing for equipment #{}: {}", equipment.getId(), ex.getMessage());
        }
    }

    private Equipment findEquipment(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + id));
    }

    private EquipmentResponse toResponse(Equipment equipment) {
        EquipmentResponse response = new EquipmentResponse();
        response.setId(equipment.getId());
        response.setName(equipment.getName());
        response.setAssetTag(equipment.getAssetTag());
        response.setDescription(equipment.getDescription());
        response.setCategory(equipment.getLegacyCategory());
        response.setModel(equipment.getModel());
        response.setManufacturer(equipment.getManufacturer());
        response.setSerialNumber(equipment.getSerialNumber());
        response.setLocation(equipment.getLocation());
        response.setSpecifications(equipment.getSpecifications());
        response.setDocumentationUrl(equipment.getDocumentationUrl());
        response.setLegacyTags(equipment.getLegacyTags());
        response.setStatus(equipment.getStatus());
        response.setPurchaseCost(equipment.getPurchaseCost());
        response.setHourlyCost(equipment.getHourlyCost());
        response.setPurchaseDate(equipment.getPurchaseDate());
        response.setDepartmentId(equipment.getDepartmentId());
        response.setInstitutionId(equipment.getInstitutionId());
        response.setCollegeId(equipment.getCollegeId());
        response.setIsShareable(equipment.getIsShareable());
        response.setRequiresApproval(equipment.getRequiresApproval());
        response.setOperatingStartTime(equipment.getOperatingStartTime());
        response.setOperatingEndTime(equipment.getOperatingEndTime());
        response.setCalibrationDate(equipment.getCalibrationDate());
        response.setCertificationInfo(equipment.getCertificationInfo());
        response.setMaxBookingHours(equipment.getMaxBookingHours());
        response.setActive(equipment.isActive());
        if (equipment.getCategory() != null) {
            response.setCategoryId(equipment.getCategory().getId());
            response.setCategoryName(equipment.getCategory().getName());
        } else {
            response.setCategoryName(equipment.getLegacyCategory());
        }
        response.setTags(equipment.getTagEntities().stream()
                .map(tag -> new EquipmentResponse.TagSummary(tag.getId(), tag.getName()))
                .toList());

        List<CalibrationRecord> records = calibrationRecordRepository
                .findByEquipmentIdOrderByCalibrationDateDesc(equipment.getId());
        if (records.isEmpty()) {
            response.setCalibrationState("NOT_RECORDED");
        } else {
            CalibrationRecord latest = records.get(0);
            response.setLastCalibrationDate(latest.getCalibrationDate());
            response.setNextCalibrationDate(latest.getNextCalibrationDate());
            LocalDate nextDate = latest.getNextCalibrationDate();
            LocalDate today = LocalDate.now(IST_ZONE);
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
}
