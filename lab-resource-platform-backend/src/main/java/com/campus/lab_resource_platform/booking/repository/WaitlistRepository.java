package com.campus.lab_resource_platform.booking.repository;

import com.campus.lab_resource_platform.booking.entity.WaitlistEntry;
import com.campus.lab_resource_platform.booking.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WaitlistEntry> findByEquipmentIdAndStatusOrderByPriorityAscCreatedAtAsc(Long equipmentId, WaitlistStatus status);

    List<WaitlistEntry> findByEquipmentIdOrderByCreatedAtDesc(Long equipmentId);

    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.equipment.id = :equipmentId AND w.status = 'WAITING' AND (w.priority < :priority OR (w.priority = :priority AND w.createdAt <= :createdAt))")
    long calculateQueuePosition(
            @Param("equipmentId") Long equipmentId,
            @Param("priority") Integer priority,
            @Param("createdAt") Instant createdAt
    );

    @Query("SELECT w FROM WaitlistEntry w WHERE w.equipment.id = :equipmentId " +
           "AND w.status = 'WAITING' " +
           "AND w.desiredStartTime < :freedEnd AND w.desiredEndTime > :freedStart " +
           "ORDER BY w.priority ASC, w.createdAt ASC")
    List<WaitlistEntry> findEligibleWaitlistCandidates(
            @Param("equipmentId") Long equipmentId,
            @Param("freedStart") Instant freedStart,
            @Param("freedEnd") Instant freedEnd
    );

    boolean existsByEquipmentIdAndUserIdAndStatus(Long equipmentId, Long userId, WaitlistStatus status);
}

