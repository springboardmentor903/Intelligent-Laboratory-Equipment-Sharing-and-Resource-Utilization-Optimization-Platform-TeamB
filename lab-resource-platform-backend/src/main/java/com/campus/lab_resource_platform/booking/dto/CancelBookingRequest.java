package com.campus.lab_resource_platform.booking.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancelBookingRequest {

    // reason is optional - student may cancel without providing a reason
    private String reason;
}
