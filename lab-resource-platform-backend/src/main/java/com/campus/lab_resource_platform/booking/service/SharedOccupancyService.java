package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingHold;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHoldRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SharedOccupancyService {

    private final BookingRepository bookingRepository;
    private final BookingHoldRepository holdRepository;

    public SharedOccupancyService(BookingRepository bookingRepository, BookingHoldRepository holdRepository) {
        this.bookingRepository = bookingRepository;
        this.holdRepository = holdRepository;
    }

    public static class OccupancySlot {
        private final Long id;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final boolean external;
        private final String slotType; // "BOOKING", "HOLD"
        private final Long partnerInstitutionId;

        public OccupancySlot(Long id, LocalDateTime startTime, LocalDateTime endTime,
                             boolean external, String slotType, Long partnerInstitutionId) {
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.external = external;
            this.slotType = slotType;
            this.partnerInstitutionId = partnerInstitutionId;
        }

        public Long getId() { return id; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public boolean isExternal() { return external; }
        public String getSlotType() { return slotType; }
        public Long getPartnerInstitutionId() { return partnerInstitutionId; }
    }

    public List<OccupancySlot> getOccupiedSlots(Long equipmentId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        List<OccupancySlot> slots = new ArrayList<>();

        // Active bookings
        Set<BookingStatus> activeStatuses = Set.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_USE
        );

        List<Booking> bookings = bookingRepository.findScheduleBookings(equipmentId, rangeStart, rangeEnd, activeStatuses);
        for (Booking b : bookings) {
            Long partnerInstId = (b.isExternal() && b.getUser() != null) ? b.getUser().getInstitutionId() : null;
            slots.add(new OccupancySlot(
                    b.getId(),
                    b.getStartTime(),
                    b.getEndTime(),
                    b.isExternal(),
                    "BOOKING",
                    partnerInstId
            ));
        }

        // Active holds
        List<BookingHold> holds = holdRepository.findActiveHoldsInPeriod(equipmentId, rangeStart, rangeEnd, Instant.now());
        for (BookingHold h : holds) {
            slots.add(new OccupancySlot(
                    h.getId(),
                    h.getStartTime(),
                    h.getEndTime(),
                    false,
                    "HOLD",
                    null
            ));
        }

        return slots;
    }

    public boolean isSlotOccupied(Long equipmentId, LocalDateTime startTime, LocalDateTime endTime, Long excludeBookingId) {
        Set<BookingStatus> activeStatuses = Set.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_USE
        );

        List<Booking> conflicts = bookingRepository.findConflictingBookings(
                equipmentId, startTime, endTime,
                Set.of(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.COMPLETED)
        );

        for (Booking b : conflicts) {
            if (activeStatuses.contains(b.getStatus())) {
                if (excludeBookingId == null || !b.getId().equals(excludeBookingId)) {
                    return true;
                }
            }
        }

        List<BookingHold> holds = holdRepository.findActiveHoldsInPeriod(equipmentId, startTime, endTime, Instant.now());
        return !holds.isEmpty();
    }
}

