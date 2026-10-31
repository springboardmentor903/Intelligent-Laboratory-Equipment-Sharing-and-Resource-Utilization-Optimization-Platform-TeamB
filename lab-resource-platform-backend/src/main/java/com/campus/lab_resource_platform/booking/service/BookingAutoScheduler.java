package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingHistory;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class BookingAutoScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingAutoScheduler.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository bookingHistoryRepository;

    public BookingAutoScheduler(
            BookingRepository bookingRepository,
            BookingHistoryRepository bookingHistoryRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingHistoryRepository = bookingHistoryRepository;
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
