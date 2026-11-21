package com.campus.lab_resource_platform.maintenance.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequestReviewDto {

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;
}
