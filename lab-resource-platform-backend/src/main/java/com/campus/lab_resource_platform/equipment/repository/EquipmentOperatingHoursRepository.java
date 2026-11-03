package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.EquipmentOperatingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EquipmentOperatingHoursRepository extends JpaRepository<EquipmentOperatingHours, Long> {

    List<EquipmentOperatingHours> findByEquipmentIdOrderByDayOfWeekAsc(Long equipmentId);

    void deleteByEquipmentId(Long equipmentId);

    @Query("SELECT o FROM EquipmentOperatingHours o WHERE o.equipment.id IN :equipmentIds")
    List<EquipmentOperatingHours> findByEquipmentIdIn(@Param("equipmentIds") Collection<Long> equipmentIds);
}
