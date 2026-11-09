package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

@Service
public class OperatingHoursCapacityService {

    public static final LocalTime DEFAULT_OPERATING_START = LocalTime.of(8, 0);
    public static final LocalTime DEFAULT_OPERATING_END = LocalTime.of(18, 0);

    /**
     * Calculates the weekly capacity in minutes for equipment from Monday to Sunday.
     */
    public long getWeeklyOperatingHoursCapacity(Equipment equipment, LocalDate dateInWeek) {
        LocalDate monday = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalTime opStart = getOperatingStartTime(equipment);
        LocalTime opEnd = getOperatingEndTime(equipment);

        long dailyMinutes = calculateDailyOperatingMinutes(opStart, opEnd);
        return dailyMinutes * 7; // Monday to Sunday (7 days)
    }

    public long calculateDailyOperatingMinutes(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0L;
        }
        return Duration.between(start, end).toMinutes();
    }

    /**
     * Overlap function: Calculates the exact booked minutes that fall within the equipment's
     * operating hours across all days of the booking that fall within the given week (Monday-Sunday).
     */
    public long calculateBookedMinutesInOperatingHours(
            LocalDateTime bookingStart,
            LocalDateTime bookingEnd,
            LocalDate weekStartMonday,
            LocalDate weekEndSunday,
            LocalTime opStart,
            LocalTime opEnd) {

        if (bookingStart == null || bookingEnd == null || !bookingEnd.isAfter(bookingStart)) {
            return 0L;
        }

        long totalOverlapMinutes = 0L;
        LocalDate current = weekStartMonday;

        while (!current.isAfter(weekEndSunday)) {
            LocalDateTime dayOpStart = current.atTime(opStart);
            LocalDateTime dayOpEnd = current.atTime(opEnd);

            LocalDateTime effectiveStart = bookingStart.isAfter(dayOpStart) ? bookingStart : dayOpStart;
            LocalDateTime effectiveEnd = bookingEnd.isBefore(dayOpEnd) ? bookingEnd : dayOpEnd;

            if (effectiveEnd.isAfter(effectiveStart)) {
                totalOverlapMinutes += Duration.between(effectiveStart, effectiveEnd).toMinutes();
            }

            current = current.plusDays(1);
        }

        return totalOverlapMinutes;
    }

    public LocalTime getOperatingStartTime(Equipment equipment) {
        if (equipment != null && equipment.getOperatingStartTime() != null) {
            return equipment.getOperatingStartTime();
        }
        return DEFAULT_OPERATING_START;
    }

    public LocalTime getOperatingEndTime(Equipment equipment) {
        if (equipment != null && equipment.getOperatingEndTime() != null) {
            return equipment.getOperatingEndTime();
        }
        return DEFAULT_OPERATING_END;
    }
}

