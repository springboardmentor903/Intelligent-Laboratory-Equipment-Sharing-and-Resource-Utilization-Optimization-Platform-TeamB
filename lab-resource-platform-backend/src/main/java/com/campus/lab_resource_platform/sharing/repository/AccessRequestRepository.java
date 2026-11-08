package com.campus.lab_resource_platform.sharing.repository;

import com.campus.lab_resource_platform.sharing.entity.AccessRequest;
import com.campus.lab_resource_platform.sharing.entity.AccessRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    @EntityGraph(attributePaths = {"equipment", "requester"})
    Page<AccessRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "requester"})
    Page<AccessRequest> findByDepartmentIdAndRequestingInstitutionIdOrderByCreatedAtDesc(
            Long departmentId, Long requestingInstitutionId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "requester"})
    Page<AccessRequest> findByRequestingInstitutionIdOrderByCreatedAtDesc(
            Long requestingInstitutionId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "requester"})
    Page<AccessRequest> findByOwnerInstitutionIdAndStatusInOrderByCreatedAtDesc(
            Long ownerInstitutionId, Collection<AccessRequestStatus> statuses, Pageable pageable);

    @Query("""
        SELECT ar FROM AccessRequest ar
        WHERE ar.equipment.id = :equipmentId
          AND ar.requester.id = :requesterId
          AND ar.status IN :openStatuses
          AND ar.startDate <= :endDate
          AND ar.endDate >= :startDate
    """)
    List<AccessRequest> findOverlappingOpenRequests(
            @Param("equipmentId") Long equipmentId,
            @Param("requesterId") Long requesterId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("openStatuses") Collection<AccessRequestStatus> openStatuses
    );

    Optional<AccessRequest> findFirstByEquipmentIdAndRequesterIdOrderByCreatedAtDesc(
            Long equipmentId, Long requesterId);

    @Query("""
        SELECT ar FROM AccessRequest ar
        WHERE ar.requester.id = :requesterId
          AND ar.equipment.id IN :equipmentIds
        ORDER BY ar.createdAt DESC
    """)
    List<AccessRequest> findByRequesterIdAndEquipmentIdIn(
            @Param("requesterId") Long requesterId,
            @Param("equipmentIds") Collection<Long> equipmentIds);
}

