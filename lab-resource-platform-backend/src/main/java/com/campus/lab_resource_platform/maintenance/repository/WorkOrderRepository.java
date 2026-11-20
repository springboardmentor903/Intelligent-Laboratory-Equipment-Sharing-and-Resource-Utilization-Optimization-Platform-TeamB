package com.campus.lab_resource_platform.maintenance.repository;

import com.campus.lab_resource_platform.maintenance.entity.WorkOrder;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderStatus;
import com.campus.lab_resource_platform.maintenance.entity.WorkOrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByWorkOrderNumber(String workOrderNumber);

    @Query("SELECT MAX(w.workOrderNumber) FROM WorkOrder w WHERE w.workOrderNumber LIKE :prefix%")
    String findMaxWorkOrderNumberWithPrefix(@Param("prefix") String prefix);

    @Query("""
        SELECT w FROM WorkOrder w
        JOIN w.equipment e
        WHERE (e.institutionId = :institutionId OR e.collegeId = :institutionId)
          AND (:status IS NULL OR w.status = :status)
          AND (:type IS NULL OR w.type = :type)
          AND (:equipmentId IS NULL OR e.id = :equipmentId)
          AND (:technicianId IS NULL OR (w.assignedTechnician IS NOT NULL AND w.assignedTechnician.id = :technicianId))
        ORDER BY w.createdAt DESC
    """)
    Page<WorkOrder> findByInstitutionWithFilters(
            @Param("institutionId") Long institutionId,
            @Param("status") WorkOrderStatus status,
            @Param("type") WorkOrderType type,
            @Param("equipmentId") Long equipmentId,
            @Param("technicianId") Long technicianId,
            Pageable pageable
    );

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.assignedTechnician.id = :technicianId
          AND (:status IS NULL OR w.status = :status)
        ORDER BY 
          CASE w.priority 
            WHEN 'CRITICAL' THEN 1 
            WHEN 'HIGH' THEN 2 
            WHEN 'MEDIUM' THEN 3 
            WHEN 'LOW' THEN 4 
            ELSE 5 
          END ASC, 
          w.createdAt DESC
    """)
    Page<WorkOrder> findByTechnicianWithFilters(
            @Param("technicianId") Long technicianId,
            @Param("status") WorkOrderStatus status,
            Pageable pageable
    );

    List<WorkOrder> findByEquipmentIdAndStatusIn(Long equipmentId, Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByPreventiveScheduleIdAndStatusIn(Long scheduleId, Collection<WorkOrderStatus> statuses);

    @Query("""
        SELECT COUNT(w) > 0 FROM WorkOrder w
        WHERE w.equipment.id = :equipmentId
          AND w.type = :type
          AND w.status IN :statuses
    """)
    boolean existsActiveWorkOrderByEquipmentAndType(
            @Param("equipmentId") Long equipmentId,
            @Param("type") WorkOrderType type,
            @Param("statuses") Collection<WorkOrderStatus> statuses
    );

    @Query("""
        SELECT COUNT(w) > 0 FROM WorkOrder w
        WHERE w.equipment.id = :equipmentId
          AND w.id != :excludeWorkOrderId
          AND w.status = 'IN_PROGRESS'
    """)
    boolean existsOtherInProgressWorkOrder(
            @Param("equipmentId") Long equipmentId,
            @Param("excludeWorkOrderId") Long excludeWorkOrderId
    );

    @Query("""
        SELECT w.assignedTechnician.id as technicianId, w.status as status, COUNT(w) as totalCount
        FROM WorkOrder w
        WHERE w.assignedTechnician IS NOT NULL
          AND w.assignedTechnician.institutionId = :institutionId
          AND w.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS')
        GROUP BY w.assignedTechnician.id, w.status
    """)
    List<Object[]> findTechnicianWorkloadGrouped(@Param("institutionId") Long institutionId);

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.assignedTechnician IS NOT NULL
          AND w.assignedTechnician.id = :technicianId
          AND w.expectedEndDate IS NOT NULL
          AND w.expectedEndDate > :now
          AND w.status IN ('ASSIGNED', 'IN_PROGRESS')
        ORDER BY w.expectedEndDate ASC
    """)
    List<WorkOrder> findUpcomingForTechnician(
            @Param("technicianId") Long technicianId,
            @Param("now") Instant now
    );

    List<WorkOrder> findByEquipmentIdAndStatusOrderByActualEndDateDesc(Long equipmentId, WorkOrderStatus status);
}
