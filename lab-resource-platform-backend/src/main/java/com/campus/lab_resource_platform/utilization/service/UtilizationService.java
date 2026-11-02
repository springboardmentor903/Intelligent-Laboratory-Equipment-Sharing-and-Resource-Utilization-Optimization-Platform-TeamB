package com.campus.lab_resource_platform.utilization.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.*;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import com.campus.lab_resource_platform.utilization.websocket.UtilizationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UtilizationService {

    private static final Logger log = LoggerFactory.getLogger(UtilizationService.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final UtilizationRepository utilizationRepository;
    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CollegeRepository collegeRepository;
    private final UtilizationWebSocketHandler webSocketHandler;

    public UtilizationService(
            UtilizationRepository utilizationRepository,
            EquipmentRepository equipmentRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            CollegeRepository collegeRepository,
            UtilizationWebSocketHandler webSocketHandler) {
        this.utilizationRepository = utilizationRepository;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.collegeRepository = collegeRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Check-in / Session Start
     * Can be performed using bookingId OR equipmentId.
     */
    @Transactional
    public UtilizationResponse checkIn(CheckInRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        Equipment equipment;
        Booking booking = null;

        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.getBookingId()));
            equipment = booking.getEquipment();

            // Transition booking to IN_USE
            booking.setStatus(BookingStatus.IN_USE);
            bookingRepository.save(booking);
        } else if (request.getEquipmentId() != null) {
            equipment = equipmentRepository.findById(request.getEquipmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + request.getEquipmentId()));
        } else {
            throw new BadRequestException("Either bookingId or equipmentId must be provided");
        }

        // Verify if equipment is already actively occupied in utilization
        Optional<Utilization> existingActive = utilizationRepository.findFirstByEquipmentIdAndStatus(equipment.getId(), "ACTIVE");
        if (existingActive.isPresent()) {
            Utilization stale = existingActive.get();
            boolean isStale = false;
            if (stale.getBooking() != null && (stale.getBooking().getStatus() == BookingStatus.COMPLETED || stale.getBooking().getStatus() == BookingStatus.CANCELLED)) {
                isStale = true;
            } else if (stale.getBooking() != null && stale.getBooking().getEndTime() != null && !Instant.now().isBefore(stale.getBooking().getEndTime())) {
                isStale = true;
            } else if (stale.getCheckInTime() != null && Duration.between(stale.getCheckInTime(), Instant.now()).toHours() >= 4) {
                isStale = true;
            }

            if (isStale) {
                log.info("Auto-closing stale active session #{} on equipment #{} prior to new check-in", stale.getId(), equipment.getId());
                stale.setCheckOutTime(Instant.now());
                stale.setStatus("COMPLETED");
                stale.setActualDurationMinutes(Math.max(1, Duration.between(stale.getCheckInTime(), Instant.now()).toMinutes()));
                utilizationRepository.save(stale);
            } else {
                throw new BadRequestException("Equipment '" + equipment.getName() + "' is already actively checked in by another session");
            }
        }

        Instant now = Instant.now();
        Utilization utilization = Utilization.builder()
                .booking(booking)
                .equipment(equipment)
                .user(user)
                .checkInTime(now)
                .operatorNotes(request.getNotes())
                .status("ACTIVE")
                .build();

        Utilization saved = utilizationRepository.save(utilization);

        // Update equipment live status to IN_USE
        equipment.setStatus(EquipmentStatus.IN_USE);
        equipmentRepository.save(equipment);

        String colName = getCollegeName(equipment);
        UtilizationResponse response = UtilizationResponse.fromEntity(saved, colName);

        // Broadcast real-time WebSocket update
        broadcastLiveEvent("CHECK_IN", response);

        log.info("Check-in session #{} started for equipment #{} ({}) by user {}",
                saved.getId(), equipment.getId(), equipment.getName(), user.getEmail());

        return response;
    }

    /**
     * Check-out / Session Stop
     */
    @Transactional
    public UtilizationResponse checkOut(Long utilizationId, CheckOutRequest request, String userEmail) {
        Utilization utilization = utilizationRepository.findById(utilizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Active utilization session not found: " + utilizationId));

        if (!"ACTIVE".equalsIgnoreCase(utilization.getStatus())) {
            throw new BadRequestException("Session #" + utilizationId + " is already closed with status: " + utilization.getStatus());
        }

        Instant now = Instant.now();
        utilization.setCheckOutTime(now);
        utilization.setStatus("COMPLETED");

        long durationMinutes = Math.max(1, Duration.between(utilization.getCheckInTime(), now).toMinutes());
        utilization.setActualDurationMinutes(durationMinutes);

        if (request != null && request.getNotes() != null && !request.getNotes().isBlank()) {
            String existingNotes = utilization.getOperatorNotes() != null ? utilization.getOperatorNotes() + " | " : "";
            utilization.setOperatorNotes(existingNotes + request.getNotes());
        }

        Utilization saved = utilizationRepository.save(utilization);

        // Update Equipment Status back to AVAILABLE (if it was IN_USE)
        Equipment equipment = utilization.getEquipment();
        if (equipment != null && equipment.getStatus() == EquipmentStatus.IN_USE) {
            equipment.setStatus(EquipmentStatus.AVAILABLE);
            equipmentRepository.save(equipment);
        }

        // If tied to a booking, complete the booking
        if (utilization.getBooking() != null) {
            Booking booking = utilization.getBooking();
            if (booking.getStatus() == BookingStatus.IN_USE || booking.getStatus() == BookingStatus.CONFIRMED) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
            }
        }

        String colName = getCollegeName(equipment);
        UtilizationResponse response = UtilizationResponse.fromEntity(saved, colName);

        // Broadcast real-time WebSocket update
        broadcastLiveEvent("CHECK_OUT", response);

        log.info("Check-out completed for session #{}, equipment #{} ({}). Duration: {} mins",
                saved.getId(), equipment.getId(), equipment.getName(), durationMinutes);

        return response;
    }

    /**
     * Check-out by Booking ID directly
     */
    @Transactional
    public UtilizationResponse checkOutByBooking(Long bookingId, CheckOutRequest request, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        Equipment equipment = booking.getEquipment();
        User user = getUserByEmail(userEmail);

        // Try to find active utilization session by bookingId
        Optional<Utilization> activeOpt = utilizationRepository.findFirstByBookingIdAndStatus(bookingId, "ACTIVE");

        // If not found by bookingId, try by equipmentId
        if (activeOpt.isEmpty() && equipment != null) {
            activeOpt = utilizationRepository.findFirstByEquipmentIdAndStatus(equipment.getId(), "ACTIVE");
        }

        Instant now = Instant.now();
        Utilization utilization;

        if (activeOpt.isPresent()) {
            utilization = activeOpt.get();
            utilization.setCheckOutTime(now);
            long durationMinutes = Math.max(1, Duration.between(utilization.getCheckInTime(), now).toMinutes());
            utilization.setActualDurationMinutes(durationMinutes);
            utilization.setStatus("COMPLETED");
            if (request != null && request.getNotes() != null && !request.getNotes().isBlank()) {
                String existing = utilization.getOperatorNotes() != null ? utilization.getOperatorNotes() + " | " : "";
                utilization.setOperatorNotes(existing + request.getNotes());
            }
        } else {
            // Graceful fallback: If booking was marked IN_USE directly without an active utilization row
            Instant checkIn = (booking.getStartTime() != null && booking.getStartTime().isBefore(now))
                    ? booking.getStartTime()
                    : now.minusSeconds(180);
            long durationMinutes = Math.max(1, Duration.between(checkIn, now).toMinutes());

            utilization = Utilization.builder()
                    .booking(booking)
                    .equipment(equipment)
                    .user(booking.getUser() != null ? booking.getUser() : user)
                    .checkInTime(checkIn)
                    .checkOutTime(now)
                    .actualDurationMinutes(durationMinutes)
                    .operatorNotes(request != null && request.getNotes() != null ? request.getNotes() : "Completed session via Check-Out")
                    .status("COMPLETED")
                    .build();
        }

        Utilization saved = utilizationRepository.save(utilization);

        // Transition booking to COMPLETED
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        // Set equipment back to AVAILABLE
        if (equipment != null) {
            equipment.setStatus(EquipmentStatus.AVAILABLE);
            equipmentRepository.save(equipment);
        }

        String colName = getCollegeName(equipment);
        UtilizationResponse response = UtilizationResponse.fromEntity(saved, colName);

        // Broadcast real-time WebSocket update
        broadcastLiveEvent("CHECK_OUT", response);

        log.info("Check-out completed for booking #{}, equipment #{} ({}). Logged duration: {} mins",
                bookingId, equipment != null ? equipment.getId() : null,
                equipment != null ? equipment.getName() : "Unknown", saved.getActualDurationMinutes());

        return response;
    }

    /**
     * Get Live Status for all equipment (optionally filtered by college)
     */
    @Transactional(readOnly = true)
    public List<EquipmentLiveStatusDto> getLiveEquipmentStatuses(Long collegeId) {
        List<Equipment> equipments = equipmentRepository.findAll();
        if (collegeId != null) {
            equipments = equipments.stream()
                    .filter(e -> collegeId.equals(e.getCollegeId()) || collegeId.equals(e.getInstitutionId()))
                    .toList();
        }

        Map<Long, College> collegeMap = collegeRepository.findAll().stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        // Active sessions map by equipmentId
        Map<Long, Utilization> activeMap = utilizationRepository.findAllActiveSessions().stream()
                .collect(Collectors.toMap(u -> u.getEquipment().getId(), Function.identity(), (a, b) -> a));

        // Today's start in IST
        LocalDate today = LocalDate.now(IST_ZONE);
        Instant startOfToday = today.atStartOfDay(IST_ZONE).toInstant();
        Instant oneWeekAgo = today.minusDays(7).atStartOfDay(IST_ZONE).toInstant();

        List<Utilization> recentUtilizations = utilizationRepository.findRecentUtilizations(oneWeekAgo);

        // Group past minutes by equipment
        Map<Long, Long> todayMinutesByEq = new HashMap<>();
        Map<Long, Long> weeklyMinutesByEq = new HashMap<>();
        Map<Long, Long> completedSessionsByEq = new HashMap<>();

        for (Utilization u : recentUtilizations) {
            Long eqId = u.getEquipment().getId();
            long mins = u.getActualDurationMinutes() != null ? u.getActualDurationMinutes() :
                    (u.getCheckOutTime() != null ? Duration.between(u.getCheckInTime(), u.getCheckOutTime()).toMinutes() : 0);

            weeklyMinutesByEq.put(eqId, weeklyMinutesByEq.getOrDefault(eqId, 0L) + mins);
            if (u.getCheckInTime().isAfter(startOfToday)) {
                todayMinutesByEq.put(eqId, todayMinutesByEq.getOrDefault(eqId, 0L) + mins);
            }
            if ("COMPLETED".equalsIgnoreCase(u.getStatus())) {
                completedSessionsByEq.put(eqId, completedSessionsByEq.getOrDefault(eqId, 0L) + 1);
            }
        }

        Instant now = Instant.now();

        return equipments.stream().map(e -> {
            Long cId = e.getCollegeId() != null ? e.getCollegeId() : e.getInstitutionId();
            String cName = cId != null && collegeMap.containsKey(cId) ? collegeMap.get(cId).getName() : "Affiliated College";

            Utilization active = activeMap.get(e.getId());
            boolean isInUse = active != null || e.getStatus() == EquipmentStatus.IN_USE;

            Long elapsed = 0L;
            if (active != null) {
                elapsed = Duration.between(active.getCheckInTime(), now).toMinutes();
            }

            long todayMins = todayMinutesByEq.getOrDefault(e.getId(), 0L) + elapsed;
            long weeklyMins = weeklyMinutesByEq.getOrDefault(e.getId(), 0L) + elapsed;

            // Lab operating window = 12 hours (720 mins/day)
            double todayRate = Math.min(100.0, Math.round(((double) todayMins / 720.0) * 1000.0) / 10.0);
            double weeklyRate = Math.min(100.0, Math.round(((double) weeklyMins / (720.0 * 7.0)) * 1000.0) / 10.0);

            return EquipmentLiveStatusDto.builder()
                    .equipmentId(e.getId())
                    .equipmentName(e.getName())
                    .model(e.getModel())
                    .category(e.getCategory())
                    .location(e.getLocation())
                    .collegeId(cId)
                    .collegeName(cName)
                    .status(e.getStatus())
                    .isCurrentlyInUse(isInUse)
                    .currentUtilizationId(active != null ? active.getId() : null)
                    .currentBookingId(active != null && active.getBooking() != null ? active.getBooking().getId() : null)
                    .currentUserName(active != null && active.getUser() != null ? active.getUser().getFullName() : null)
                    .currentUserEmail(active != null && active.getUser() != null ? active.getUser().getEmail() : null)
                    .sessionStartTime(active != null ? active.getCheckInTime() : null)
                    .elapsedMinutes(elapsed)
                    .todayUtilizationRatePercent(todayRate)
                    .weeklyUtilizationRatePercent(weeklyRate)
                    .totalUsageMinutesToday(todayMins)
                    .totalSessionsCompleted(completedSessionsByEq.getOrDefault(e.getId(), 0L))
                    .build();
        }).toList();
    }

    /**
     * Overall College / Platform Utilization Summary
     */
    @Transactional(readOnly = true)
    public UtilizationSummaryDto getUtilizationSummary(Long collegeId) {
        List<EquipmentLiveStatusDto> statuses = getLiveEquipmentStatuses(collegeId);

        long available = statuses.stream().filter(s -> s.getStatus() == EquipmentStatus.AVAILABLE).count();
        long inUse = statuses.stream().filter(s -> s.isCurrentlyInUse() || s.getStatus() == EquipmentStatus.IN_USE).count();
        long maintenance = statuses.stream().filter(s -> s.getStatus() == EquipmentStatus.UNDER_MAINTENANCE).count();
        long decommissioned = statuses.stream().filter(s -> s.getStatus() == EquipmentStatus.DECOMMISSIONED).count();

        double avgRate = statuses.isEmpty() ? 0.0 :
                Math.round(statuses.stream().mapToDouble(EquipmentLiveStatusDto::getTodayUtilizationRatePercent).average().orElse(0.0) * 10.0) / 10.0;

        long totalTodayMins = statuses.stream().mapToLong(EquipmentLiveStatusDto::getTotalUsageMinutesToday).sum();

        String colName = "All Colleges";
        if (collegeId != null) {
            College c = collegeRepository.findById(collegeId).orElse(null);
            if (c != null) colName = c.getName();
        }

        return UtilizationSummaryDto.builder()
                .collegeId(collegeId)
                .collegeName(colName)
                .totalEquipments(statuses.size())
                .availableCount(available)
                .inUseCount(inUse)
                .underMaintenanceCount(maintenance)
                .decommissionedCount(decommissioned)
                .overallUtilizationRatePercent(avgRate)
                .totalUsageMinutesToday(totalTodayMins)
                .activeSessionsCount(inUse)
                .equipments(statuses)
                .build();
    }

    /**
     * Get historical usage logs for an equipment
     */
    @Transactional(readOnly = true)
    public List<UtilizationResponse> getEquipmentUsageHistory(Long equipmentId) {
        Equipment eq = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + equipmentId));
        String colName = getCollegeName(eq);
        return utilizationRepository.findByEquipmentIdOrderByCheckInTimeDesc(equipmentId)
                .stream()
                .map(u -> UtilizationResponse.fromEntity(u, colName))
                .toList();
    }

    /**
     * Get user's own session history
     */
    @Transactional(readOnly = true)
    public List<UtilizationResponse> getMyUsageHistory(String userEmail) {
        User user = getUserByEmail(userEmail);
        return utilizationRepository.findByUserIdOrderByCheckInTimeDesc(user.getId())
                .stream()
                .map(u -> UtilizationResponse.fromEntity(u, getCollegeName(u.getEquipment())))
                .toList();
    }

    private void broadcastLiveEvent(String eventType, UtilizationResponse data) {
        try {
            String json = String.format(
                    "{\"type\":\"%s\",\"equipmentId\":%d,\"utilizationId\":%d,\"status\":\"%s\",\"timestamp\":\"%s\"}",
                    eventType,
                    data.getEquipmentId(),
                    data.getId(),
                    data.getStatus(),
                    Instant.now().toString()
            );
            webSocketHandler.broadcastEvent(json);
        } catch (Exception e) {
            log.warn("Could not broadcast live utilization event: {}", e.getMessage());
        }
    }

    private String getCollegeName(Equipment e) {
        if (e == null) return "Affiliated College";
        Long colId = e.getCollegeId() != null ? e.getCollegeId() : e.getInstitutionId();
        if (colId != null) {
            College c = collegeRepository.findById(colId).orElse(null);
            if (c != null) return c.getName();
        }
        return "Affiliated College";
    }

    private User getUserByEmail(String email) {
        if (email == null) {
            throw new BadRequestException("User email cannot be null");
        }
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
