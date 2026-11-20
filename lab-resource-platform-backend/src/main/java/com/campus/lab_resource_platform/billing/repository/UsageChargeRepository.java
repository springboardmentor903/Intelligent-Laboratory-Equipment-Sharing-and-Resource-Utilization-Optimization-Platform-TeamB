package com.campus.lab_resource_platform.billing.repository;

import com.campus.lab_resource_platform.billing.entity.ChargeType;
import com.campus.lab_resource_platform.billing.entity.UsageCharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageChargeRepository extends JpaRepository<UsageCharge, Long> {

    Optional<UsageCharge> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    Page<UsageCharge> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT c FROM UsageCharge c
        WHERE c.institutionId = :institutionId
          AND (:chargeType IS NULL OR c.chargeType = :chargeType)
          AND (:departmentId IS NULL OR c.departmentId = :departmentId)
          AND (:equipmentId IS NULL OR c.equipment.id = :equipmentId)
          AND (:startDate IS NULL OR c.createdAt >= :startDate)
          AND (:endDate IS NULL OR c.createdAt <= :endDate)
        ORDER BY c.createdAt DESC
    """)
    Page<UsageCharge> findByInstitutionWithFilters(
            @Param("institutionId") Long institutionId,
            @Param("chargeType") ChargeType chargeType,
            @Param("departmentId") Long departmentId,
            @Param("equipmentId") Long equipmentId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @Query("""
        SELECT c FROM UsageCharge c
        WHERE c.institutionId = :institutionId
          AND (:startDate IS NULL OR c.createdAt >= :startDate)
          AND (:endDate IS NULL OR c.createdAt <= :endDate)
        ORDER BY c.createdAt DESC
    """)
    List<UsageCharge> findAllByInstitutionInPeriod(
            @Param("institutionId") Long institutionId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        SELECT c FROM UsageCharge c
        WHERE c.departmentId = :departmentId
          AND c.createdAt >= :startDate
          AND c.createdAt <= :endDate
    """)
    List<UsageCharge> findByDepartmentInPeriod(
            @Param("departmentId") Long departmentId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
