package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingHistory;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import com.campus.lab_resource_platform.utilization.websocket.UtilizationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingAutoScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingAutoScheduler.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository bookingHistoryRepository;
    private final WaitlistService waitlistService;
    private final EquipmentRepository equipmentRepository;
    private final UtilizationRepository utilizationRepository;
    private final UtilizationWebSocketHandler webSocketHandler;

    public BookingAutoScheduler(
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository,
            @Lazy WaitlistService waitlistService,
            EquipmentRepository equipmentRepository,
            UtilizationRepository utilizationRepository,
            @Lazy UtilizationWebSocketHandler webSocketHandler) {
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
        this.waitlistService = waitlistService;
        this.equipmentRepository = equipmentRepository;
        this.utilizationRepository = utilizationRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Runs every 10 seconds to inspect booking schedules in Indian Standard Time (IST).
     * 1) If a booking is IN_USE and its endTime has passed (now >= endTime),
     *    automatically mark it as COMPLETED.
     * 2) If a booking is NOT IN_USE (and not already COMPLETED/CANCELLED) and 1 minute has elapsed
     *    past its startTime (now >= startTime + 1 min), automatically change status directly to CANCELLED.
     */
    @Scheduled(fixedRate = 10000)
    public void scheduledBookingLifecycle() {
        processBookingLifecycle();
    }

    /**
     * Process lifecycle transitions. Can also be called on-demand before returning booking lists.
     */
    @Transactional
    public void processBookingLifecycle() {
        Instant now = Instant.now();
        ZonedDateTime nowIst = now.atZone(IST_ZONE);

        List<Booking> allBookings = bookingRepository.findAll();

        for (Booking booking : allBookings) {
            try {
                processSingleBooking(booking, now, nowIst);
            } catch (Exception e) {
                log.error("Error processing lifecycle for booking #{}: {}", booking.getId(), e.getMessage(), e);
            }
        }

        try {
            reconcileActiveSessionsAndEquipments(now);
        } catch (Exception e) {
            log.error("Error reconciling active sessions and equipments: {}", e.getMessage(), e);
        }
    }

    /**
     * Reconciles orphaned active sessions and stuck equipment statuses across the platform.
     */
    public void reconcileActiveSessionsAndEquipments(Instant now) {
        // 1. Reconcile any orphaned ACTIVE utilization sessions
        List<Utilization> activeSessions = utilizationRepository.findAllActiveSessions();
        for (Utilization u : activeSessions) {
            boolean shouldComplete = false;
            String reason = null;

            Booking b = u.getBooking();
            if (b != null) {
                if (b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CANCELLED) {
                    shouldComplete = true;
                    reason = "Booking already " + b.getStatus();
                } else if (b.getEndTime() != null && !now.isBefore(b.getEndTime())) {
                    shouldComplete = true;
                    reason = "Booking scheduled end time reached";
                    b.setStatus(BookingStatus.COMPLETED);
                    bookingRepository.saveAndFlush(b);
                }
            } else {
                // Session without booking (ad-hoc): check if equipment has any active booking or if running > 1 hour
                Equipment eq = u.getEquipment();
                boolean hasActiveBooking = false;
                if (eq != null) {
                    List<Booking> activeB = bookingRepository.findConflictingBookings(
                            eq.getId(),
                            now.minusSeconds(1),
                            now.plusSeconds(1),
                            List.of(BookingStatus.IN_USE, BookingStatus.CONFIRMED)
                    );
                    hasActiveBooking = !activeB.isEmpty();
                }

                if (!hasActiveBooking && u.getCheckInTime() != null && Duration.between(u.getCheckInTime(), now).toMinutes() >= 30) {
                    shouldComplete = true;
                    reason = "No active booking and ad-hoc duration exceeded 30 minutes";
                } else if (u.getCheckInTime() != null && Duration.between(u.getCheckInTime(), now).toHours() >= 2) {
                    shouldComplete = true;
                    reason = "Maximum ad-hoc session duration reached";
                }
            }

            if (shouldComplete) {
                log.info("Self-healing: Auto-completing active utilization session #{} for equipment #{} (Reason: {})",
                        u.getId(), u.getEquipment() != null ? u.getEquipment().getId() : null, reason);
                u.setCheckOutTime(now);
                u.setStatus("COMPLETED");
                long mins = Math.max(1, Duration.between(u.getCheckInTime() != null ? u.getCheckInTime() : now.minusSeconds(120), now).toMinutes());
                u.setActualDurationMinutes(mins);
                if (u.getOperatorNotes() == null || u.getOperatorNotes().isBlank()) {
                    u.setOperatorNotes("Auto-completed by system: " + reason);
                }
                utilizationRepository.saveAndFlush(u);

                if (u.getEquipment() != null) {
                    Equipment eq = u.getEquipment();
                    eq.setStatus(EquipmentStatus.AVAILABLE);
                    equipmentRepository.saveAndFlush(eq);

                    if (webSocketHandler != null) {
                        String json = String.format(
                                "{\"type\":\"CHECK_OUT\",\"equipmentId\":%d,\"utilizationId\":%d,\"status\":\"COMPLETED\",\"timestamp\":\"%s\"}",
                                eq.getId(),
                                u.getId(),
                                now.toString()
                        );
                        webSocketHandler.broadcastEvent(json);

                        String statusJson = String.format(
                                "{\"type\":\"STATUS_CHANGE\",\"equipmentId\":%d,\"status\":\"AVAILABLE\",\"timestamp\":\"%s\"}",
                                eq.getId(),
                                now.toString()
                        );
                        webSocketHandler.broadcastEvent(statusJson);
                    }
                }
            }
        }

        // 2. Reconcile any equipments stuck in IN_USE status
        utilizationRepository.flush();
        equipmentRepository.flush();
        List<Equipment> allEquipments = equipmentRepository.findAll();
        for (Equipment eq : allEquipments) {
            if (eq.getStatus() == EquipmentStatus.IN_USE) {
                boolean hasActiveSession = utilizationRepository.findFirstByEquipmentIdAndStatus(eq.getId(), "ACTIVE").isPresent();
                boolean hasActiveBooking = false;
                List<Booking> eqBookings = bookingRepository.findConflictingBookings(
                        eq.getId(),
                        now.minusSeconds(1),
                        now.plusSeconds(1),
                        List.of(BookingStatus.IN_USE)
                );
                if (!eqBookings.isEmpty()) {
                    hasActiveBooking = true;
                }

                if (!hasActiveSession && !hasActiveBooking) {
                    log.info("Self-healing: Resetting stuck IN_USE equipment #{} ({}) back to AVAILABLE", eq.getId(), eq.getName());
                    eq.setStatus(EquipmentStatus.AVAILABLE);
                    equipmentRepository.saveAndFlush(eq);

                    if (webSocketHandler != null) {
                        String json = String.format(
                                "{\"type\":\"STATUS_CHANGE\",\"equipmentId\":%d,\"status\":\"AVAILABLE\",\"timestamp\":\"%s\"}",
                                eq.getId(),
                                now.toString()
                        );
                        webSocketHandler.broadcastEvent(json);
                    }
                }
            }
        }
    }

    private void processSingleBooking(Booking booking, Instant now, ZonedDateTime nowIst) {
        if (booking.getStartTime() == null) return;

        BookingStatus currentStatus = booking.getStatus();
        if (currentStatus == BookingStatus.COMPLETED || currentStatus == BookingStatus.CANCELLED) {
            return;
        }

        // Grace threshold: 1 minute after scheduled start time (e.g., 9:15 PM -> 9:16 PM)
        Instant graceThreshold = booking.getStartTime().plus(Duration.ofMinutes(1));
        boolean pastGracePeriod = !now.isBefore(graceThreshold);

        if (currentStatus == BookingStatus.IN_USE) {
            // Case 1: If IN_USE, auto-complete after the selected time range ends (now >= endTime)
            if (booking.getEndTime() != null && !now.isBefore(booking.getEndTime())) {
                log.info(">>> [IST Lifecycle] Auto-completing IN_USE booking #{} (End: {} IST, Current: {} IST)",
                        booking.getId(), booking.getEndTime().atZone(IST_ZONE), nowIst);

                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.saveAndFlush(booking);

                saveHistoryQuietly(booking, currentStatus, BookingStatus.COMPLETED, now,
                        "System auto-completed: Selected time range ended in IST");

                // Complete utilization session and reset equipment status
                Equipment equipment = booking.getEquipment();
                if (equipment != null) {
                    equipment.setStatus(EquipmentStatus.AVAILABLE);
                    equipmentRepository.saveAndFlush(equipment);
                }

                try {
                    // Close ALL active utilization sessions for this equipment (whether linked by booking or equipmentId)
                    List<Utilization> activeByEq = utilizationRepository.findByEquipmentIdOrderByCheckInTimeDesc(equipment != null ? equipment.getId() : -1L);
                    for (Utilization u : activeByEq) {
                        if ("ACTIVE".equalsIgnoreCase(u.getStatus())) {
                            u.setCheckOutTime(now);
                            long mins = Math.max(1, Duration.between(u.getCheckInTime() != null ? u.getCheckInTime() : now.minusSeconds(180), now).toMinutes());
                            u.setActualDurationMinutes(mins);
                            u.setStatus("COMPLETED");
                            u.setOperatorNotes("Auto-completed by scheduler at booking end time");
                            utilizationRepository.saveAndFlush(u);
                        }
                    }

                    if (webSocketHandler != null && equipment != null) {
                        String json = String.format(
                                "{\"type\":\"CHECK_OUT\",\"equipmentId\":%d,\"utilizationId\":0,\"status\":\"COMPLETED\",\"timestamp\":\"%s\"}",
                                equipment.getId(),
                                Instant.now().toString()
                        );
                        webSocketHandler.broadcastEvent(json);

                        String statusJson = String.format(
                                "{\"type\":\"STATUS_CHANGE\",\"equipmentId\":%d,\"status\":\"AVAILABLE\",\"timestamp\":\"%s\"}",
                                equipment.getId(),
                                Instant.now().toString()
                        );
                        webSocketHandler.broadcastEvent(statusJson);
                    }
                } catch (Exception ex) {
                    log.warn("Could not record utilization completion for auto-completed booking #{}: {}", booking.getId(), ex.getMessage());
                }
            }
        } else {
            // Case 2: Status is anything EXCEPT IN_USE (PENDING_APPROVAL, CONFIRMED, etc.)
            // If 1 minute has passed after start time (e.g. at/after 9:16 PM for 9:15 PM start):
            // automatically change status directly to CANCELLED so other students can book!
            if (pastGracePeriod) {
                log.info(">>> [IST Lifecycle] Auto-cancelling booking #{} (status was {}, Start: {} IST, Current: {} IST)",
                        booking.getId(), currentStatus, booking.getStartTime().atZone(IST_ZONE), nowIst);

                booking.setStatus(BookingStatus.CANCELLED);
                booking.setCancelledAt(now);
                booking.setCancellationReason("Auto-cancelled: Booking was not transitioned to IN_USE within 1 minute after start time");
                booking.setRejectionReason("Auto-cancelled: Booking was not transitioned to IN_USE within 1 minute after start time");
                bookingRepository.saveAndFlush(booking);

                saveHistoryQuietly(booking, currentStatus, BookingStatus.CANCELLED, now,
                        "System auto-cancelled: 1-minute start time grace period elapsed without IN_USE status");

                // Ensure equipment is available if it was tied to this booking
                if (booking.getEquipment() != null && booking.getEquipment().getStatus() == EquipmentStatus.IN_USE) {
                    booking.getEquipment().setStatus(EquipmentStatus.AVAILABLE);
                    equipmentRepository.saveAndFlush(booking.getEquipment());
                }

                // Auto-promote any waiting student for this released slot
                if (booking.getEquipment() != null) {
                    waitlistService.checkAndPromoteWaitlistedCandidate(
                            booking.getEquipment().getId(),
                            booking.getStartTime(),
                            booking.getEndTime()
                    );
                }
            }
        }
    }

    private void saveHistoryQuietly(Booking booking, BookingStatus prev, BookingStatus next, Instant now, String remarks) {
        try {
            bookingHistoryRepository.save(BookingHistory.builder()
                    .booking(booking)
                    .previousStatus(prev)
                    .newStatus(next)
                    .changedBy(booking.getUser() != null ? booking.getUser() : null)
                    .timestamp(now)
                    .changedAt(now)
                    .remarks(remarks)
                    .build());
        } catch (Exception ex) {
            log.warn("Could not record booking history for #{} transition {} -> {}: {}",
                    booking.getId(), prev, next, ex.getMessage());
        }
    }
}
