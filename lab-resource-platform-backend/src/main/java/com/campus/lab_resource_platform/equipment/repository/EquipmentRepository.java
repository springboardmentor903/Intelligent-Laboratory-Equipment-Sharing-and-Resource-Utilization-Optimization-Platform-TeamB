package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

}

