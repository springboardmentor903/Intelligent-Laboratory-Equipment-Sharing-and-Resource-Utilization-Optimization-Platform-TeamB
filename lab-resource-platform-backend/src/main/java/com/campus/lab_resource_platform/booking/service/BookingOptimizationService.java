package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.dto.SlotRecommendationResponse;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BookingOptimizationService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<BookingStatus> BLOCKING = List.of(
            BookingStatus.PENDING_APPROVAL,
            BookingStatus.CONFIRMED,
            BookingStatus.IN_USE
    );

    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;

    public BookingOptimizationService(
            BookingRepository bookingRepository,
            EquipmentRepository equipmentRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.equipmentRepository = equipmentRepository;
    }

    /**
     * Rule-based slot optimization:
     * - Searches operating hours (08:00 AM - 08:00 PM IST)
     * - Identifies contiguous free slots to reduce lab idle fragmentation
     * - Favors lower contention / off-peak hours
     * - Calculates score 0-100 and human-readable reasoning
     */
    @Transactional(readOnly = true)
    public SlotRecommendationResponse recommendSlots(Long equipmentId, LocalDate queryDate, int durationMinutes) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + equipmentId));

        LocalDate targetDate = (queryDate != null) ? queryDate : LocalDate.now(IST_ZONE);
        int validDuration = Math.max(30, Math.min(durationMinutes, equipment.getMaxBookingHours() * 60));

        // Define lab day operating window: 08:00 to 20:00 IST
        ZonedDateTime dayStart = targetDate.atTime(8, 0).atZone(IST_ZONE);
        ZonedDateTime dayEnd = targetDate.atTime(20, 0).atZone(IST_ZONE);

        Instant rangeStart = dayStart.toInstant();
        Instant rangeEnd = dayEnd.toInstant();

        // If today, start from now + 15 min buffer if now is after 8 AM
        Instant now = Instant.now();
        if (rangeStart.isBefore(now)) {
            rangeStart = now.plus(Duration.ofMinutes(15));
        }

        List<Booking> occupied = bookingRepository.findOccupiedSlots(equipmentId, dayStart.toInstant(), dayEnd.toInstant(), BLOCKING);

        List<SlotRecommendationResponse.RecommendedSlotDto> candidates = new ArrayList<>();

        // Generate 30-minute stepping candidate slots
        Instant current = rangeStart;
        while (current.plus(Duration.ofMinutes(validDuration)).isBefore(rangeEnd) ||
               current.plus(Duration.ofMinutes(validDuration)).equals(rangeEnd)) {

            Instant slotStart = current;
            Instant slotEnd = current.plus(Duration.ofMinutes(validDuration));

            // Check if slot overlaps with any occupied booking
            boolean hasOverlap = occupied.stream().anyMatch(b ->
                    slotStart.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime())
            );

            if (!hasOverlap) {
                // Rule 1: Contiguity check (adjacent to existing booking saves idle fragments)
                boolean isContiguous = occupied.stream().anyMatch(b ->
                        Duration.between(b.getEndTime(), slotStart).abs().toMinutes() <= 15 ||
                        Duration.between(slotEnd, b.getStartTime()).abs().toMinutes() <= 15
                );

                // Rule 2: Off-peak check (before 11 AM or between 3 PM - 6 PM IST)
                ZonedDateTime istSlotStart = slotStart.atZone(IST_ZONE);
                int hour = istSlotStart.getHour();
                boolean isOffPeak = (hour >= 8 && hour < 11) || (hour >= 15 && hour < 18);

                // Calculate optimization score
                int score = 70;
                StringBuilder reason = new StringBuilder("Available conflict-free slot");

                if (isContiguous) {
                    score += 15;
                    reason.append(" • Minimizes laboratory idle fragmentation");
                }
                if (isOffPeak) {
                    score += 15;
                    reason.append(" • Off-peak low laboratory contention");
                } else {
                    reason.append(" • Peak laboratory hours");
                }

                candidates.add(SlotRecommendationResponse.RecommendedSlotDto.builder()
                        .startTime(slotStart)
                        .endTime(slotEnd)
                        .score(Math.min(100, score))
                        .reason(reason.toString())
                        .isContiguous(isContiguous)
                        .isOffPeak(isOffPeak)
                        .build());
            }

            current = current.plus(Duration.ofMinutes(30));
        }

        // Sort descending by score, limit to top 5 recommendations
        candidates.sort(Comparator.comparingInt(SlotRecommendationResponse.RecommendedSlotDto::getScore).reversed());
        List<SlotRecommendationResponse.RecommendedSlotDto> top = candidates.stream().limit(5).toList();

        return SlotRecommendationResponse.builder()
                .equipmentId(equipment.getId())
                .equipmentName(equipment.getName())
                .queryDate(targetDate.toString())
                .durationMinutes(validDuration)
                .recommendations(top)
                .message(top.isEmpty() ? "No free slots remaining on this date. Consider joining the waitlist or checking another date." : "Found " + top.size() + " optimal conflict-free slots.")
                .build();
    }
}

