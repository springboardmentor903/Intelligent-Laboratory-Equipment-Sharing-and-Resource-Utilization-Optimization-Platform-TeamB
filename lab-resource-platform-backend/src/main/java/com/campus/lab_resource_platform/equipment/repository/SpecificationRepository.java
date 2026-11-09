
package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecificationRepository
        extends JpaRepository<Specification, Long> {

    List<Specification> findByEquipmentId(Long equipmentId);

    Optional<Specification> findByIdAndEquipmentId(
            Long id, Long equipmentId);
}
