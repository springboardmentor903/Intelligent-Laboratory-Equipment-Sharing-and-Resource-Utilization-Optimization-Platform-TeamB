package com.campus.lab_resource_platform.utilization.repository;

import com.campus.lab_resource_platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilizationRepository extends JpaRepository<Utilization, Long> {

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    Optional<Utilization> findByBookingId(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    List<Utilization> findByBookingIdIn(Collection<Long> bookingIds);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    List<Utilization> findByEquipmentIdOrderByCheckInTimeDesc(Long equipmentId);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    List<Utilization> findByUserIdOrderByCheckInTimeDesc(Long userId);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    List<Utilization> findByStatusOrderByCheckInTimeDesc(String status);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    Optional<Utilization> findFirstByEquipmentIdAndStatus(Long equipmentId, String status);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    Optional<Utilization> findFirstByBookingIdAndStatus(Long bookingId, String status);

    @Query("SELECT u FROM Utilization u WHERE u.checkInTime >= :since ORDER BY u.checkInTime DESC")
    List<Utilization> findRecentUtilizations(@Param("since") Instant since);

    @Query("SELECT u FROM Utilization u WHERE u.equipment.id = :equipmentId AND u.checkInTime >= :since")
    List<Utilization> findByEquipmentSince(
            @Param("equipmentId") Long equipmentId,
            @Param("since") Instant since
    );

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("SELECT u FROM Utilization u WHERE u.status = 'ACTIVE'")
    List<Utilization> findAllActiveSessions();

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("""
        SELECT u FROM Utilization u
        WHERE u.equipment.id IN :equipmentIds
          AND (u.checkOutTime IS NULL OR u.checkOutTime >= :rangeStart)
          AND u.checkInTime <= :rangeEnd
        ORDER BY u.checkInTime ASC
    """)
    List<Utilization> findUtilizationsInRangeForEquipments(
            @Param("equipmentIds") Collection<Long> equipmentIds,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("""
        SELECT u FROM Utilization u
        WHERE (u.equipment.institutionId = :institutionId OR u.equipment.collegeId = :institutionId)
          AND u.usedMinutes IS NOT NULL
        ORDER BY u.checkInTime DESC
    """)
    List<Utilization> findAllProvidedUtilizations(@Param("institutionId") Long institutionId);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("""
        SELECT u FROM Utilization u
        WHERE (u.user.institutionId = :institutionId OR u.user.collegeId = :institutionId)
          AND (u.equipment.institutionId != :institutionId AND (u.equipment.collegeId IS NULL OR u.equipment.collegeId != :institutionId))
          AND u.usedMinutes IS NOT NULL
        ORDER BY u.checkInTime DESC
    """)
    List<Utilization> findAllUsedExternalUtilizations(@Param("institutionId") Long institutionId);

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("""
        SELECT u FROM Utilization u
        WHERE (u.equipment.institutionId = :institutionId OR u.equipment.collegeId = :institutionId)
          AND u.usedMinutes IS NOT NULL
          AND u.checkInTime >= :rangeStart AND u.checkInTime <= :rangeEnd
        ORDER BY u.checkInTime ASC
    """)
    List<Utilization> findProvidedUtilizationsBetween(
            @Param("institutionId") Long institutionId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("""
        SELECT u FROM Utilization u
        WHERE (u.user.institutionId = :institutionId OR u.user.collegeId = :institutionId)
          AND (u.equipment.institutionId != :institutionId AND (u.equipment.collegeId IS NULL OR u.equipment.collegeId != :institutionId))
          AND u.usedMinutes IS NOT NULL
          AND u.checkInTime >= :rangeStart AND u.checkInTime <= :rangeEnd
        ORDER BY u.checkInTime ASC
    """)
    List<Utilization> findUsedExternalUtilizationsBetween(
            @Param("institutionId") Long institutionId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

    @EntityGraph(attributePaths = {"booking", "equipment", "user"})
    @Query("""
        SELECT u FROM Utilization u
        WHERE (u.equipment.institutionId = :institutionId OR u.equipment.collegeId = :institutionId)
          AND u.usedMinutes IS NOT NULL
    """)
    List<Utilization> findAllByEquipmentInstitution(@Param("institutionId") Long institutionId);
}
