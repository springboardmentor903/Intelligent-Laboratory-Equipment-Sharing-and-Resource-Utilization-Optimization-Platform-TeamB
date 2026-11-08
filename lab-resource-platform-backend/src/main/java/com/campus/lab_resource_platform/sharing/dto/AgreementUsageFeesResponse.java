package com.campus.lab_resource_platform.sharing.dto;

import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AgreementUsageFeesResponse {

    private Long agreementId;
    private Long equipmentId;
    private String equipmentName;
    private BigDecimal feePerHour;

    private Long totalBookedMinutes = 0L;
    private Long totalUsedMinutes = 0L;
    private BigDecimal totalEstimatedFee = BigDecimal.ZERO;
    private BigDecimal totalActualFee = BigDecimal.ZERO;

    private List<BookingFeeItem> items = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BookingFeeItem {
        private Long bookingId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long bookedMinutes;
        private Long usedMinutes;
        private BigDecimal feePerHour;
        private BigDecimal estimatedFee;
        private BigDecimal actualFee;
        private BookingStatus status;
    }
}

