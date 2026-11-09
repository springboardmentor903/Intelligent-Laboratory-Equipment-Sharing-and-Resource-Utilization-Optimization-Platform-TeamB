
package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.EquipmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentCategoryRepository
        extends JpaRepository<EquipmentCategory, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<EquipmentCategory> findByNameIgnoreCase(String name);
}
