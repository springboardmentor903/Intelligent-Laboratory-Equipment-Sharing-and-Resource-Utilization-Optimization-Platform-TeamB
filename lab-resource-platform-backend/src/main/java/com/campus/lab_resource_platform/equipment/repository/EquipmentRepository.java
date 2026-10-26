
package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EquipmentRepository
        extends JpaRepository<Equipment, Long>,
                JpaSpecificationExecutor<Equipment> {

    boolean existsByAssetTagIgnoreCase(String assetTag);

    Optional<Equipment> findByAssetTagIgnoreCase(String assetTag);
}
