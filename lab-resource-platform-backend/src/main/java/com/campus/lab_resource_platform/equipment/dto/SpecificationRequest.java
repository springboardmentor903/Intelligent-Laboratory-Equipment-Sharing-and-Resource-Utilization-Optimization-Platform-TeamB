
package com.campus.lab_resource_platform.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SpecificationRequest {

    @NotBlank(message = "Specification name is required")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Specification value is required")
    @Size(max = 1000)
    private String value;

    @Size(max = 50)
    private String unit;
}
