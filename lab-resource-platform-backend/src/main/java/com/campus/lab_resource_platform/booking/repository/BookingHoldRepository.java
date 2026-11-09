package com.campus.lab_resource_platform.booking.repository;

import com.campus.lab_resource_platform.booking.entity.BookingHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingHoldRepository extends JpaRepository<BookingHold, Long> {

    @Query("""
        SELECT h FROM BookingHold h
        WHERE h.equipment.id = :equipmentId
          AND h.active = true
          AND h.expiresAt > :now
          AND h.startTime < :endTime
          AND h.endTime > :startTime
    """)
    List<BookingHold> findActiveHoldsInPeriod(
            @Param("equipmentId") Long equipmentId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("now") Instant now
    );
}

