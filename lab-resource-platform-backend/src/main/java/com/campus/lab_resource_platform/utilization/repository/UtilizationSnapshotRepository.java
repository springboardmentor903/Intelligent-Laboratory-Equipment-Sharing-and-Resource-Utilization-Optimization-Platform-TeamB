package com.campus.lab_resource_platform.utilization.repository;

import com.campus.lab_resource_platform.utilization.entity.UtilizationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilizationSnapshotRepository extends JpaRepository<UtilizationSnapshot, Long> {

    Optional<UtilizationSnapshot> findByEquipmentIdAndSnapshotDate(Long equipmentId, LocalDate snapshotDate);

    @Query("SELECT s FROM UtilizationSnapshot s WHERE s.equipment.id = :equipmentId AND s.snapshotDate BETWEEN :startDate AND :endDate ORDER BY s.snapshotDate ASC")
    List<UtilizationSnapshot> findByEquipmentAndDateRange(
            @Param("equipmentId") Long equipmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT s FROM UtilizationSnapshot s WHERE s.equipment.id IN :equipmentIds AND s.snapshotDate BETWEEN :startDate AND :endDate ORDER BY s.snapshotDate ASC")
    List<UtilizationSnapshot> findByEquipmentIdsAndDateRange(
            @Param("equipmentIds") Collection<Long> equipmentIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
