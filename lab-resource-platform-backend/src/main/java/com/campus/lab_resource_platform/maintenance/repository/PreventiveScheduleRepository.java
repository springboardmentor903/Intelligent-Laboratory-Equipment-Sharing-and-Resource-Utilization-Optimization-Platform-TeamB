package com.campus.lab_resource_platform.maintenance.repository;

import com.campus.lab_resource_platform.maintenance.entity.PreventiveSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PreventiveScheduleRepository extends JpaRepository<PreventiveSchedule, Long> {

    @Query("""
        SELECT s FROM PreventiveSchedule s
        JOIN s.equipment e
        WHERE (e.institutionId = :institutionId OR e.collegeId = :institutionId)
          AND (:equipmentId IS NULL OR e.id = :equipmentId)
          AND (:activeOnly IS NULL OR s.active = :activeOnly)
        ORDER BY s.nextDueDate ASC
    """)
    Page<PreventiveSchedule> findByInstitutionWithFilters(
            @Param("institutionId") Long institutionId,
            @Param("equipmentId") Long equipmentId,
            @Param("activeOnly") Boolean activeOnly,
            Pageable pageable
    );

    List<PreventiveSchedule> findByEquipmentId(Long equipmentId);

    @Query("""
        SELECT s FROM PreventiveSchedule s
        WHERE s.active = true
          AND s.nextDueDate <= :cutoffDate
    """)
    List<PreventiveSchedule> findDueSchedules(@Param("cutoffDate") LocalDate cutoffDate);
}
