package com.campus.lab_resource_platform.maintenance.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingHistory;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.entity.WaitlistEntry;
import com.campus.lab_resource_platform.booking.entity.WaitlistStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.repository.WaitlistRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.maintenance.dto.*;
import com.campus.lab_resource_platform.maintenance.entity.*;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequest;
import com.campus.lab_resource_platform.maintenance.repository.*;
import com.campus.lab_resource_platform.sharing.event.ReleasedTimeEvent;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class WorkOrderService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderServiceLogRepository serviceLogRepository;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRequestRepository requestRepository;
    private final PreventiveScheduleRepository scheduleRepository;
    private final EquipmentDowntimeRepository downtimeRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository bookingHistoryRepository;
    private final WaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            WorkOrderServiceLogRepository serviceLogRepository,
            EquipmentRepository equipmentRepository,
            MaintenanceRequestRepository requestRepository,
            PreventiveScheduleRepository scheduleRepository,
            EquipmentDowntimeRepository downtimeRepository,
            CalibrationRecordRepository calibrationRecordRepository,
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository,
            WaitlistRepository waitlistRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.workOrderRepository = workOrderRepository;
        this.serviceLogRepository = serviceLogRepository;
        this.equipmentRepository = equipmentRepository;
        this.requestRepository = requestRepository;
        this.scheduleRepository = scheduleRepository;
        this.downtimeRepository = downtimeRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
        this.waitlistRepository = waitlistRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public synchronized String generateNextWorkOrderNumber() {
        int currentYear = LocalDate.now(IST_ZONE).getYear();
        String prefix = "WO-" + currentYear + "-";
        String maxNumber = workOrderRepository.findMaxWorkOrderNumberWithPrefix(prefix);
        int nextSeq = 1;
        if (maxNumber != null && maxNumber.length() >= prefix.length() + 6) {
            try {
                String seqStr = maxNumber.substring(prefix.length());
                nextSeq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException ignored) {}
        }
        return String.format("%s%06d", prefix, nextSeq);
    }

    @Transactional
    public WorkOrderResponse createWorkOrder(WorkOrderCreateDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + dto.getEquipmentId()));

        validateLabManagerOfEquipment(user, equipment);
        return WorkOrderResponse.fromEntity(createWorkOrderInternal(dto, user));
    }

    @Transactional
    public WorkOrder createWorkOrderInternal(WorkOrderCreateDto dto, User creator) {
        Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + dto.getEquipmentId()));

        User technician = null;
        if (dto.getAssignedTechnicianId() != null) {
            technician = userRepository.findById(dto.getAssignedTechnicianId())
                    .orElseThrow(() -> new ResourceNotFoundException("Technician not found with ID: " + dto.getAssignedTechnicianId()));
            validateTechnicianBelongsToSameInstitution(technician, equipment);
        }

        MaintenanceRequest request = null;
        if (dto.getMaintenanceRequestId() != null) {
            request = requestRepository.findById(dto.getMaintenanceRequestId()).orElse(null);
        }

        PreventiveSchedule schedule = null;
        if (dto.getPreventiveScheduleId() != null) {
            schedule = scheduleRepository.findById(dto.getPreventiveScheduleId()).orElse(null);
        }

        String woNumber = generateNextWorkOrderNumber();
        WorkOrderStatus initialStatus = technician != null ? WorkOrderStatus.ASSIGNED : WorkOrderStatus.OPEN;

        WorkOrder workOrder = WorkOrder.builder()
                .workOrderNumber(woNumber)
                .type(dto.getType())
                .status(initialStatus)
                .equipment(equipment)
                .maintenanceRequest(request)
                .preventiveSchedule(schedule)
                .assignedTechnician(technician)
                .createdBy(creator)
                .title(dto.getTitle().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : "")
                .priority(dto.getPriority() != null ? dto.getPriority() : MaintenancePriority.MEDIUM)
                .expectedStartDate(dto.getExpectedStartDate())
                .expectedEndDate(dto.getExpectedEndDate())
                .cost(dto.getEstimatedCost())
                .build();

        WorkOrder saved = workOrderRepository.save(workOrder);

        // Record initial service log
        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(saved)
                .author(creator)
                .action("CREATED")
                .notes("Work order created with status " + initialStatus +
                        (technician != null ? " and assigned to " + technician.getFullName() : ""))
                .timestamp(Instant.now())
                .build();
        serviceLogRepository.save(log);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> getWorkOrders(
            WorkOrderStatus status,
            WorkOrderType type,
            Long equipmentId,
            Long technicianId,
            String userEmail,
            Pageable pageable) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        if (user.getRole() == User.Role.LAB_TECHNICIAN) {
            // Lab Technicians can access only their assigned work orders
            return workOrderRepository.findByTechnicianWithFilters(user.getId(), status, pageable)
                    .map(WorkOrderResponse::fromEntity);
        }

        if (user.getRole() != User.Role.LAB_MANAGER && user.getRole() != User.Role.INSTITUTION_ADMINISTRATOR) {
            throw new ForbiddenException("Only Lab Managers, Institution Administrators and assigned Technicians can view work orders");
        }

        Long instId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        if (instId == null) {
            instId = 1L;
        }

        return workOrderRepository.findByInstitutionWithFilters(instId, status, type, equipmentId, technicianId, pageable)
                .map(WorkOrderResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrderById(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateAccessToWorkOrder(user, workOrder);
        return WorkOrderResponse.fromEntity(workOrder);
    }

    @Transactional
    public WorkOrderResponse assignTechnician(Long id, WorkOrderAssignDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateLabManagerOfEquipment(user, workOrder.getEquipment());

        if (workOrder.getStatus() == WorkOrderStatus.COMPLETED || workOrder.getStatus() == WorkOrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot assign technician to a " + workOrder.getStatus() + " work order");
        }

        User technician = userRepository.findById(dto.getTechnicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found with ID: " + dto.getTechnicianId()));

        if (technician.getRole() != User.Role.LAB_TECHNICIAN) {
            throw new BadRequestException("User " + technician.getFullName() + " does not have the LAB_TECHNICIAN role");
        }

        validateTechnicianBelongsToSameInstitution(technician, workOrder.getEquipment());

        workOrder.setAssignedTechnician(technician);
        if (workOrder.getStatus() == WorkOrderStatus.OPEN) {
            workOrder.setStatus(WorkOrderStatus.ASSIGNED);
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        // Add service log
        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(saved)
                .author(user)
                .action("ASSIGNED")
                .notes("Assigned to technician " + technician.getFullName() +
                        (dto.getNotes() != null && !dto.getNotes().isBlank() ? ": " + dto.getNotes().trim() : ""))
                .timestamp(Instant.now())
                .build();
        serviceLogRepository.save(log);

        return WorkOrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public StartPreviewResponse previewStartWorkOrder(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateAccessToWorkOrder(user, workOrder);

        Instant start = workOrder.getExpectedStartDate() != null ? workOrder.getExpectedStartDate() : Instant.now();
        Instant end = workOrder.getExpectedEndDate() != null ? workOrder.getExpectedEndDate() : start.plus(Duration.ofHours(8));

        List<Booking> overlapping = bookingRepository.findConflictingBookings(
                workOrder.getEquipment().getId(),
                start,
                end,
                List.of(BookingStatus.PENDING_APPROVAL, BookingStatus.CONFIRMED)
        );

        List<StartPreviewResponse.ConflictedBookingDto> conflicts = overlapping.stream()
                .map(b -> StartPreviewResponse.ConflictedBookingDto.builder()
                        .bookingId(b.getId())
                        .userId(b.getUser() != null ? b.getUser().getId() : null)
                        .userName(b.getUser() != null ? b.getUser().getFullName() : null)
                        .userEmail(b.getUser() != null ? b.getUser().getEmail() : null)
                        .startTime(b.getStartTime())
                        .endTime(b.getEndTime())
                        .status(b.getStatus() != null ? b.getStatus().name() : null)
                        .purpose(b.getPurpose())
                        .build())
                .toList();

        return StartPreviewResponse.builder()
                .workOrderId(workOrder.getId())
                .workOrderNumber(workOrder.getWorkOrderNumber())
                .equipmentId(workOrder.getEquipment().getId())
                .equipmentName(workOrder.getEquipment().getName())
                .maintenanceStartDate(start)
                .maintenanceEndDate(end)
                .totalConflictedBookings(conflicts.size())
                .conflictedBookings(conflicts)
                .build();
    }

    @Transactional
    public WorkOrderResponse startWorkOrder(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateTechnicianOrManager(user, workOrder);

        if (workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS) {
            throw new BadRequestException("Work order is already in progress");
        }
        if (workOrder.getStatus() == WorkOrderStatus.COMPLETED || workOrder.getStatus() == WorkOrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot start a " + workOrder.getStatus() + " work order");
        }

        Instant now = Instant.now();
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrder.setActualStartDate(now);
        if (workOrder.getExpectedEndDate() == null) {
            workOrder.setExpectedEndDate(now.plus(Duration.ofHours(8)));
        }

        Equipment equipment = workOrder.getEquipment();
        equipment.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        equipmentRepository.save(equipment);

        // Record downtime start
        EquipmentDowntime downtime = EquipmentDowntime.builder()
                .equipment(equipment)
                .workOrder(workOrder)
                .startTime(now)
                .reason("Maintenance Work Order: " + workOrder.getWorkOrderNumber() + " - " + workOrder.getTitle())
                .build();
        downtimeRepository.save(downtime);

        // Automatically cancel PENDING_APPROVAL and CONFIRMED bookings that overlap maintenance
        cancelOverlappingBookingsAndNotify(workOrder, now, workOrder.getExpectedEndDate(), user);

        // Expire open waitlist offers during the maintenance period
        expireWaitlistOffersDuringMaintenance(equipment.getId(), now, workOrder.getExpectedEndDate());

        WorkOrder saved = workOrderRepository.save(workOrder);

        // Service log
        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(saved)
                .author(user)
                .action("STARTED")
                .notes("Work order started. Equipment moved to UNDER_MAINTENANCE.")
                .timestamp(now)
                .build();
        serviceLogRepository.save(log);

        return WorkOrderResponse.fromEntity(saved);
    }

    @Transactional
    public WorkOrderResponse extendWorkOrder(Long id, WorkOrderExtendDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateTechnicianOrManager(user, workOrder);

        if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            throw new BadRequestException("Only work orders currently IN_PROGRESS can be extended");
        }

        if (dto.getNewExpectedEndDate().isBefore(Instant.now())) {
            throw new BadRequestException("New expected end date must be in the future");
        }

        Instant previousEnd = workOrder.getExpectedEndDate() != null ? workOrder.getExpectedEndDate() : Instant.now();
        workOrder.setExpectedEndDate(dto.getNewExpectedEndDate());

        // Cancel any additional overlapping bookings during the extended period
        cancelOverlappingBookingsAndNotify(workOrder, previousEnd, dto.getNewExpectedEndDate(), user);
        expireWaitlistOffersDuringMaintenance(workOrder.getEquipment().getId(), previousEnd, dto.getNewExpectedEndDate());

        WorkOrder saved = workOrderRepository.save(workOrder);

        // Service log
        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(saved)
                .author(user)
                .action("EXTENDED")
                .notes("Maintenance extended to " + dto.getNewExpectedEndDate() +
                        (dto.getReason() != null ? ". Reason: " + dto.getReason().trim() : ""))
                .timestamp(Instant.now())
                .build();
        serviceLogRepository.save(log);

        return WorkOrderResponse.fromEntity(saved);
    }

    @Transactional
    public WorkOrderResponse completeWorkOrder(Long id, WorkOrderCompleteDto dto, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateTechnicianOrManager(user, workOrder);

        if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS && workOrder.getStatus() != WorkOrderStatus.ASSIGNED) {
            throw new BadRequestException("Cannot complete a work order with status " + workOrder.getStatus());
        }

        Instant now = Instant.now();
        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        workOrder.setActualEndDate(now);
        if (workOrder.getActualStartDate() == null) {
            workOrder.setActualStartDate(workOrder.getCreatedAt() != null ? workOrder.getCreatedAt() : now);
        }

        if (dto != null) {
            if (dto.getResolutionNotes() != null) {
                workOrder.setResolutionNotes(dto.getResolutionNotes().trim());
            }
            if (dto.getCost() != null) {
                workOrder.setCost(dto.getCost());
            }
        }

        // Close downtime record
        Optional<EquipmentDowntime> downtimeOpt = downtimeRepository.findFirstByWorkOrderIdOrderByStartTimeDesc(workOrder.getId());
        if (downtimeOpt.isPresent()) {
            EquipmentDowntime dt = downtimeOpt.get();
            dt.setEndTime(now);
            long totalMins = Math.max(1, Duration.between(dt.getStartTime(), now).toMinutes());
            dt.setTotalDowntimeMinutes(totalMins);
            // Calculate open hours downtime (approx 10h/day: 08:00 - 18:00 IST)
            long openHoursMins = calculateOpenHoursDowntime(dt.getStartTime(), now);
            dt.setOpenHoursDowntimeMinutes(openHoursMins);
            downtimeRepository.save(dt);
        }

        // Move equipment back to AVAILABLE provided no other work order is in progress
        Equipment equipment = workOrder.getEquipment();
        boolean otherInProgress = workOrderRepository.existsOtherInProgressWorkOrder(equipment.getId(), workOrder.getId());
        if (!otherInProgress) {
            equipment.setStatus(EquipmentStatus.AVAILABLE);
            equipmentRepository.save(equipment);
        }

        // If PREVENTIVE, update next due date on schedule
        if (workOrder.getPreventiveSchedule() != null) {
            PreventiveSchedule sched = workOrder.getPreventiveSchedule();
            sched.advanceNextDueDate(LocalDate.now(IST_ZONE));
            scheduleRepository.save(sched);
        }

        // If CALIBRATION or calibration details provided in completion DTO (Task T2)
        if (dto != null && (Boolean.TRUE.equals(dto.getIsCalibration()) || workOrder.getType() == WorkOrderType.CALIBRATION)) {
            LocalDate calDate = dto.getCalibrationDate() != null ? dto.getCalibrationDate() : LocalDate.now(IST_ZONE);
            LocalDate nextCalDate = dto.getNextCalibrationDate() != null ? dto.getNextCalibrationDate() : calDate.plusMonths(6);

            CalibrationRecord calRecord = new CalibrationRecord();
            calRecord.setEquipment(equipment);
            calRecord.setCalibrationDate(calDate);
            calRecord.setNextCalibrationDate(nextCalDate);
            calRecord.setResult(dto.getCalibrationResult() != null ? dto.getCalibrationResult().trim() : "PASSED");
            calRecord.setPerformedBy(dto.getPerformedBy() != null ? dto.getPerformedBy().trim() : user.getFullName());
            calRecord.setNotes(dto.getCalibrationNotes() != null ? dto.getCalibrationNotes().trim() : "Completed via Work Order " + workOrder.getWorkOrderNumber());
            calibrationRecordRepository.save(calRecord);

            // Also update equipment calibrationDate instant
            equipment.setCalibrationDate(calDate.atStartOfDay(IST_ZONE).toInstant());
            equipmentRepository.save(equipment);
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        // Service log
        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(saved)
                .author(user)
                .action("COMPLETED")
                .notes("Work order completed. Resolution: " +
                        (workOrder.getResolutionNotes() != null ? workOrder.getResolutionNotes() : "Maintenance successful."))
                .timestamp(now)
                .build();
        serviceLogRepository.save(log);

        return WorkOrderResponse.fromEntity(saved);
    }

    @Transactional
    public WorkOrderResponse cancelWorkOrder(Long id, String reason, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateLabManagerOfEquipment(user, workOrder.getEquipment());

        if (workOrder.getStatus() == WorkOrderStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel an already completed work order");
        }

        Instant now = Instant.now();
        boolean wasInProgress = workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS;
        workOrder.setStatus(WorkOrderStatus.CANCELLED);
        workOrder.setActualEndDate(now);

        // If was in progress, close downtime
        if (wasInProgress) {
            Optional<EquipmentDowntime> downtimeOpt = downtimeRepository.findFirstByWorkOrderIdOrderByStartTimeDesc(workOrder.getId());
            if (downtimeOpt.isPresent()) {
                EquipmentDowntime dt = downtimeOpt.get();
                dt.setEndTime(now);
                long totalMins = Math.max(1, Duration.between(dt.getStartTime(), now).toMinutes());
                dt.setTotalDowntimeMinutes(totalMins);
                dt.setOpenHoursDowntimeMinutes(calculateOpenHoursDowntime(dt.getStartTime(), now));
                downtimeRepository.save(dt);
            }

            Equipment equipment = workOrder.getEquipment();
            boolean otherInProgress = workOrderRepository.existsOtherInProgressWorkOrder(equipment.getId(), workOrder.getId());
            if (!otherInProgress) {
                equipment.setStatus(EquipmentStatus.AVAILABLE);
                equipmentRepository.save(equipment);
            }
        }

        // If linked to preventive schedule, advance or update due date
        if (workOrder.getPreventiveSchedule() != null) {
            PreventiveSchedule sched = workOrder.getPreventiveSchedule();
            sched.advanceNextDueDate(LocalDate.now(IST_ZONE));
            scheduleRepository.save(sched);
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        // Service log
        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(saved)
                .author(user)
                .action("CANCELLED")
                .notes("Work order cancelled" + (reason != null && !reason.isBlank() ? ": " + reason.trim() : ""))
                .timestamp(now)
                .build();
        serviceLogRepository.save(log);

        return WorkOrderResponse.fromEntity(saved);
    }

    @Transactional
    public WorkOrderServiceLogDto addServiceLogNote(Long id, String note, String userEmail) {
        User user = getUserByEmail(userEmail);
        validateNotSystemAdmin(user);

        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with ID: " + id));

        validateTechnicianOrManager(user, workOrder);

        if (note == null || note.trim().isBlank()) {
            throw new BadRequestException("Service log note cannot be empty");
        }

        WorkOrderServiceLog log = WorkOrderServiceLog.builder()
                .workOrder(workOrder)
                .author(user)
                .action("NOTE_ADDED")
                .notes(note.trim())
                .timestamp(Instant.now())
                .build();

        WorkOrderServiceLog saved = serviceLogRepository.save(log);
        return WorkOrderServiceLogDto.fromEntity(saved);
    }

    private void cancelOverlappingBookingsAndNotify(WorkOrder workOrder, Instant start, Instant end, User changedBy) {
        List<Booking> overlapping = bookingRepository.findConflictingBookings(
                workOrder.getEquipment().getId(),
                start,
                end,
                List.of(BookingStatus.PENDING_APPROVAL, BookingStatus.CONFIRMED)
        );

        for (Booking booking : overlapping) {
            BookingStatus prevStatus = booking.getStatus();
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancellationReason("Equipment under maintenance");
            booking.setCancelledAt(Instant.now());
            bookingRepository.save(booking);

            // Maintain booking history
            BookingHistory history = BookingHistory.builder()
                    .booking(booking)
                    .previousStatus(prevStatus)
                    .newStatus(BookingStatus.CANCELLED)
                    .changedBy(changedBy)
                    .remarks("Automatically cancelled due to maintenance work order: " + workOrder.getWorkOrderNumber())
                    .timestamp(Instant.now())
                    .build();
            bookingHistoryRepository.save(history);

            // Publish ReleasedTimeEvent for notifications in Session 4
            LocalDateTime sTime = LocalDateTime.ofInstant(booking.getStartTime(), IST_ZONE);
            LocalDateTime eTime = LocalDateTime.ofInstant(booking.getEndTime(), IST_ZONE);
            eventPublisher.publishEvent(new ReleasedTimeEvent(
                    this,
                    workOrder.getEquipment().getId(),
                    booking.getId(),
                    sTime,
                    eTime,
                    "Equipment under maintenance"
            ));
        }
    }

    private void expireWaitlistOffersDuringMaintenance(Long equipmentId, Instant start, Instant end) {
        List<WaitlistEntry> entries = waitlistRepository.findByEquipmentIdAndStatusOrderByPriorityAscCreatedAtAsc(equipmentId, WaitlistStatus.OFFERED);
        for (WaitlistEntry entry : entries) {
            if (entry.getDesiredStartTime() != null && entry.getDesiredEndTime() != null) {
                if (entry.getDesiredStartTime().isBefore(end) && entry.getDesiredEndTime().isAfter(start)) {
                    entry.setStatus(WaitlistStatus.EXPIRED);
                    waitlistRepository.save(entry);
                }
            }
        }
    }

    private long calculateOpenHoursDowntime(Instant start, Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0L;
        }
        long openMinutes = 0L;
        ZonedDateTime current = start.atZone(IST_ZONE);
        ZonedDateTime finish = end.atZone(IST_ZONE);

        while (current.isBefore(finish)) {
            int hour = current.getHour();
            // Standard open hours: 08:00 to 18:00 IST
            if (hour >= 8 && hour < 18) {
                openMinutes++;
            }
            current = current.plusMinutes(1);
        }
        return openMinutes;
    }

    private void validateNotSystemAdmin(User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            throw new ForbiddenException("System Administrators do not have access to maintenance operations");
        }
    }

    private void validateLabManagerOfEquipment(User user, Equipment equipment) {
        if (user.getRole() != User.Role.LAB_MANAGER) {
            throw new ForbiddenException("Only the Lab Manager can manage work orders");
        }
        Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
        Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
        if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
            throw new ForbiddenException("Only the Lab Manager of the equipment's institution can manage its work orders");
        }
    }

    private void validateTechnicianBelongsToSameInstitution(User technician, Equipment equipment) {
        Long techInstId = technician.getInstitutionId() != null ? technician.getInstitutionId() : technician.getCollegeId();
        Long eqInstId = equipment.getInstitutionId() != null ? equipment.getInstitutionId() : equipment.getCollegeId();
        if (techInstId != null && eqInstId != null && !Objects.equals(techInstId, eqInstId)) {
            throw new BadRequestException("Technician " + technician.getFullName() +
                    " does not belong to the same institution as the equipment");
        }
    }

    private void validateTechnicianOrManager(User user, WorkOrder workOrder) {
        if (user.getRole() == User.Role.LAB_MANAGER) {
            validateLabManagerOfEquipment(user, workOrder.getEquipment());
            return;
        }
        if (user.getRole() == User.Role.LAB_TECHNICIAN) {
            if (workOrder.getAssignedTechnician() == null || !Objects.equals(workOrder.getAssignedTechnician().getId(), user.getId())) {
                throw new ForbiddenException("Lab Technicians can only operate on work orders assigned to them");
            }
            return;
        }
        throw new ForbiddenException("Only the assigned Lab Technician or Lab Manager can perform this action");
    }

    private void validateAccessToWorkOrder(User user, WorkOrder workOrder) {
        if (user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR) {
            Long userInstId = user.getInstitutionId() != null ? user.getInstitutionId() : user.getCollegeId();
            Long eqInstId = workOrder.getEquipment().getInstitutionId() != null
                    ? workOrder.getEquipment().getInstitutionId()
                    : workOrder.getEquipment().getCollegeId();
            if (userInstId != null && eqInstId != null && !Objects.equals(userInstId, eqInstId)) {
                throw new ForbiddenException("Access denied to work order for different institution");
            }
            return;
        }
        if (user.getRole() == User.Role.LAB_MANAGER) {
            validateLabManagerOfEquipment(user, workOrder.getEquipment());
            return;
        }
        if (user.getRole() == User.Role.LAB_TECHNICIAN) {
            if (workOrder.getAssignedTechnician() == null || !Objects.equals(workOrder.getAssignedTechnician().getId(), user.getId())) {
                throw new ForbiddenException("Lab Technicians can only access work orders assigned to them");
            }
            return;
        }
        throw new ForbiddenException("Insufficient permissions to access work orders");
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
