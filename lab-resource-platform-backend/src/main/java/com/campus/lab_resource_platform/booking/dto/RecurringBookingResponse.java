package com.campus.lab_resource_platform.booking.dto;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringBookingResponse {

    private String recurrenceGroupId;
    private String recurrenceType;
    private int totalRequested;
    private int totalBooked;
    private int totalConflicted;
    @Builder.Default
    private List<BookingResponse> successfulBookings = new ArrayList<>();
    @Builder.Default
    private List<RecurringConflictDto> conflicts = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecurringConflictDto {
        private int occurrenceIndex;
        private Instant requestedStart;
        private Instant requestedEnd;
        private String conflictReason;
    }
}

