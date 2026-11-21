package com.campus.lab_resource_platform.maintenance.repository;

import com.campus.lab_resource_platform.maintenance.entity.MaintenancePriority;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequest;
import com.campus.lab_resource_platform.maintenance.entity.MaintenanceRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    Page<MaintenanceRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    @Query("""
        SELECT r FROM MaintenanceRequest r
        JOIN r.equipment e
        WHERE (e.institutionId = :institutionId OR e.collegeId = :institutionId)
          AND (:status IS NULL OR r.status = :status)
          AND (:priority IS NULL OR r.priority = :priority)
          AND (:equipmentId IS NULL OR e.id = :equipmentId)
        ORDER BY r.createdAt DESC
    """)
    Page<MaintenanceRequest> findByInstitutionWithFilters(
            @Param("institutionId") Long institutionId,
            @Param("status") MaintenanceRequestStatus status,
            @Param("priority") MaintenancePriority priority,
            @Param("equipmentId") Long equipmentId,
            Pageable pageable
    );

    List<MaintenanceRequest> findByEquipmentIdAndStatus(Long equipmentId, MaintenanceRequestStatus status);
}
