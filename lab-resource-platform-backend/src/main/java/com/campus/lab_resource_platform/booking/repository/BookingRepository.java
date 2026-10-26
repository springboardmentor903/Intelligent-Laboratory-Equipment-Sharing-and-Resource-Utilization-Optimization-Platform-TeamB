package com.campus.lab_resource_platform.booking.repository;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b WHERE b.equipment.id = :equipmentId " +
           "AND b.status IN :blockingStatuses " +
           "AND (:newStart < b.endTime AND :newEnd > b.startTime)")
    List<Booking> findConflictingBookings(
            @Param("equipmentId") Long equipmentId,
            @Param("newStart") Instant newStart,
            @Param("newEnd") Instant newEnd,
            @Param("blockingStatuses") Collection<BookingStatus> blockingStatuses
    );

    @Query("SELECT b FROM Booking b WHERE b.equipment.id = :equipmentId " +
           "AND b.status IN :blockingStatuses " +
           "AND b.endTime >= :rangeStart AND b.startTime <= :rangeEnd " +
           "ORDER BY b.startTime ASC")
    List<Booking> findOccupiedSlots(
            @Param("equipmentId") Long equipmentId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("blockingStatuses") Collection<BookingStatus> blockingStatuses
    );

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);
}
