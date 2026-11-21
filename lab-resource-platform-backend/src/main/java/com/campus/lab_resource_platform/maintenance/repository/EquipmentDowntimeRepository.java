package com.campus.lab_resource_platform.maintenance.repository;

import com.campus.lab_resource_platform.maintenance.entity.EquipmentDowntime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentDowntimeRepository extends JpaRepository<EquipmentDowntime, Long> {

    Optional<EquipmentDowntime> findFirstByWorkOrderIdOrderByStartTimeDesc(Long workOrderId);

    Optional<EquipmentDowntime> findFirstByEquipmentIdAndEndTimeIsNull(Long equipmentId);

    List<EquipmentDowntime> findByEquipmentIdOrderByStartTimeDesc(Long equipmentId);

    @Query("""
        SELECT d FROM EquipmentDowntime d
        JOIN d.equipment e
        WHERE (e.institutionId = :institutionId OR e.collegeId = :institutionId)
          AND (:departmentId IS NULL OR e.departmentId = :departmentId)
          AND (:equipmentId IS NULL OR e.id = :equipmentId)
          AND d.startTime >= :since
        ORDER BY d.startTime ASC
    """)
    List<EquipmentDowntime> findDowntimesInRange(
            @Param("institutionId") Long institutionId,
            @Param("departmentId") Long departmentId,
            @Param("equipmentId") Long equipmentId,
            @Param("since") Instant since
    );
}
