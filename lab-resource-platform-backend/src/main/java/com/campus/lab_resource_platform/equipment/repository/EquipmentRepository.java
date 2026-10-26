package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

}
