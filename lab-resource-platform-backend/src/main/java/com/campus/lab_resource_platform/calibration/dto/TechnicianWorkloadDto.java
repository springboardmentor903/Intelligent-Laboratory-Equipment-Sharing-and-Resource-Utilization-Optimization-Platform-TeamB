package com.campus.lab_resource_platform.calibration.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianWorkloadDto {

    private Long technicianId;
    private String fullName;
    private String email;
    private String phone;
    private int activeTaskCount;
    private int inProgressTaskCount;
    private int overdueTaskCount;
    private Instant nextScheduledDate;
}
