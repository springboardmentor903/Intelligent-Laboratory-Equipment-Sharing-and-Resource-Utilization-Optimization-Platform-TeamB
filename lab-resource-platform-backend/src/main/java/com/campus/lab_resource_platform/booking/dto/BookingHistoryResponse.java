package com.campus.lab_resource_platform.booking.dto;

import com.campus.lab_resource_platform.booking.entity.BookingHistory;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingHistoryResponse {

    private Long id;
    private Long bookingId;
    private BookingStatus previousStatus;
    private BookingStatus newStatus;
    private Long changedByUserId;
    private String changedByUserName;
    private Instant timestamp;
    private String remarks;

    public static BookingHistoryResponse fromEntity(BookingHistory h) {
        return BookingHistoryResponse.builder()
                .id(h.getId())
                .bookingId(h.getBooking() != null ? h.getBooking().getId() : null)
                .previousStatus(h.getPreviousStatus())
                .newStatus(h.getNewStatus())
                .changedByUserId(h.getChangedBy() != null ? h.getChangedBy().getId() : null)
                .changedByUserName(h.getChangedBy() != null ? h.getChangedBy().getFullName() : null)
                .timestamp(h.getTimestamp())
                .remarks(h.getRemarks())
                .build();
    }
}
