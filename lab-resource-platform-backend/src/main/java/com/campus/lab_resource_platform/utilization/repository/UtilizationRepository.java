package com.campus.lab_resource_platform.utilization.repository;

import com.campus.lab_resource_platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilizationRepository extends JpaRepository<Utilization, Long> {

    List<Utilization> findByEquipmentIdOrderByCheckInTimeDesc(Long equipmentId);

    List<Utilization> findByUserIdOrderByCheckInTimeDesc(Long userId);

    List<Utilization> findByStatusOrderByCheckInTimeDesc(String status);

    Optional<Utilization> findFirstByEquipmentIdAndStatus(Long equipmentId, String status);

    Optional<Utilization> findFirstByBookingIdAndStatus(Long bookingId, String status);

    @Query("SELECT u FROM Utilization u WHERE u.checkInTime >= :since ORDER BY u.checkInTime DESC")
    List<Utilization> findRecentUtilizations(@Param("since") Instant since);

    @Query("SELECT u FROM Utilization u WHERE u.equipment.id = :equipmentId AND u.checkInTime >= :since")
    List<Utilization> findByEquipmentSince(@Param("equipmentId") Long equipmentId, @Param("since") Instant since);

    @Query("SELECT u FROM Utilization u WHERE u.status = 'ACTIVE'")
    List<Utilization> findAllActiveSessions();

    @Query("SELECT u FROM Utilization u WHERE u.equipment.id IN :equipmentIds " +
           "AND (u.checkOutTime IS NULL OR u.checkOutTime >= :rangeStart) " +
           "AND u.checkInTime <= :rangeEnd " +
           "ORDER BY u.checkInTime ASC")
    List<Utilization> findUtilizationsInRangeForEquipments(
            @Param("equipmentIds") java.util.Collection<Long> equipmentIds,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );
}
