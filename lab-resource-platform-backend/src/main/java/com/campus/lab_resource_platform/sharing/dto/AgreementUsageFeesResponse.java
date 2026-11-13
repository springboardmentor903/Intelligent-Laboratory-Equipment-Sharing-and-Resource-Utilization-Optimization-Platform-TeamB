package com.campus.lab_resource_platform.sharing.dto;

import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public Double getTotalUsageHours() {
        if (totalUsedMinutes != null && totalUsedMinutes > 0) {
            return Math.round((totalUsedMinutes / 60.0) * 10.0) / 10.0;
        }
        if (totalBookedMinutes != null && totalBookedMinutes > 0) {
            return Math.round((totalBookedMinutes / 60.0) * 10.0) / 10.0;
        }
        return 0.0;
    }

    public BigDecimal getHourlyRate() {
        return feePerHour;
    }

    public BigDecimal getGrossFees() {
        if (totalActualFee != null && totalActualFee.compareTo(BigDecimal.ZERO) > 0) {
            return totalActualFee;
        }
        return totalEstimatedFee != null ? totalEstimatedFee : BigDecimal.ZERO;
    }

    public Integer getPartnerDiscount() {
        return 10;
    }

    public BigDecimal getNetPayable() {
        BigDecimal gross = getGrossFees();
        if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return gross.multiply(BigDecimal.valueOf(0.90)).setScale(2, RoundingMode.HALF_UP);
    }

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

