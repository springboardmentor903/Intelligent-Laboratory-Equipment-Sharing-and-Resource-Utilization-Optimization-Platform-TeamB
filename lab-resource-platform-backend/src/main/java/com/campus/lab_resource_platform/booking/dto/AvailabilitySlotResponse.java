package com.campus.lab_resource_platform.booking.dto;

import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilitySlotResponse {

    private Instant startTime;
    private Instant endTime;
    private BookingStatus status;
}
