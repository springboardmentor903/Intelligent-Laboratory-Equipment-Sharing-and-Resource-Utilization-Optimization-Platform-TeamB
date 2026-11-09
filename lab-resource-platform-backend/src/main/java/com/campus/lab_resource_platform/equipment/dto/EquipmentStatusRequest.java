
package com.campus.lab_resource_platform.equipment.dto;

import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EquipmentStatusRequest {

    @NotNull(message = "Equipment status is required")
    private EquipmentStatus status;
}
