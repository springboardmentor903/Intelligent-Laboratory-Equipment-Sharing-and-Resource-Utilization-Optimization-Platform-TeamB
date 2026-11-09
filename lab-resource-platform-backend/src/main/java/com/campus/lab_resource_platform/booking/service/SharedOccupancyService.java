package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingHold;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHoldRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SharedOccupancyService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

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

        List<Booking> bookings = bookingRepository.findScheduleBookings(
                equipmentId,
                rangeStart.atZone(IST_ZONE).toInstant(),
                rangeEnd.atZone(IST_ZONE).toInstant(),
                activeStatuses
        );
        for (Booking b : bookings) {
            Long partnerInstId = (b.isExternal() && b.getUser() != null) ? b.getUser().getInstitutionId() : null;
            slots.add(new OccupancySlot(
                    b.getId(),
                    LocalDateTime.ofInstant(b.getStartTime(), IST_ZONE),
                    LocalDateTime.ofInstant(b.getEndTime(), IST_ZONE),
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

    public boolean isSlotOccupied(Long equipmentId, Instant startTime, Instant endTime, Long excludeBookingId) {
        Set<BookingStatus> activeStatuses = Set.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_USE
        );

        List<Booking> conflicts = bookingRepository.findConflictingBookings(
                equipmentId, startTime, endTime, activeStatuses
        );

        for (Booking b : conflicts) {
            if (excludeBookingId == null || !b.getId().equals(excludeBookingId)) {
                return true;
            }
        }

        List<BookingHold> holds = holdRepository.findActiveHoldsInPeriod(
                equipmentId,
                LocalDateTime.ofInstant(startTime, IST_ZONE),
                LocalDateTime.ofInstant(endTime, IST_ZONE),
                Instant.now()
        );
        return !holds.isEmpty();
    }
}
