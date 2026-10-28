package com.campus.lab_resource_platform.equipment;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class EquipmentDataInitializer implements CommandLineRunner {

    private final EquipmentRepository equipmentRepository;

    public EquipmentDataInitializer(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public void run(String... args) {
        if (equipmentRepository.count() == 0) {
            Equipment defaultEquipment = Equipment.builder()
                    .name("Test Laboratory Microscope")
                    .category("Microscope")
                    .model("MIC-TEST-01")
                    .manufacturer("Test Manufacturer")
                    .status(EquipmentStatus.AVAILABLE)
                    .tags(Set.of("biology", "optics"))
                    .requiresApproval(true)
                    .maxBookingHours(4)
                    .build();

            equipmentRepository.save(defaultEquipment);
            System.out.println("Idempotent seed: Created initial test laboratory equipment.");
        }
    }
}

