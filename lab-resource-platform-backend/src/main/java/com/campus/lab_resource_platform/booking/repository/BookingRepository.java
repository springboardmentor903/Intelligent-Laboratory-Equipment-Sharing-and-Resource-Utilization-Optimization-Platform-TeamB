package com.campus.lab_resource_platform.booking.repository;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"equipment", "user"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "user"})
    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "user"})
    Page<Booking> findByUserIdOrderByStartTimeDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.status IN :blockingStatuses
          AND b.startTime < :newEnd
          AND b.endTime > :newStart
    """)
    List<Booking> findConflictingBookings(
            @Param("equipmentId") Long equipmentId,
            @Param("newStart") Instant newStart,
            @Param("newEnd") Instant newEnd,
            @Param("blockingStatuses") Collection<BookingStatus> blockingStatuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.status IN :blockingStatuses
          AND b.endTime >= :rangeStart
          AND b.startTime <= :rangeEnd
        ORDER BY b.startTime ASC
    """)
    List<Booking> findOccupiedSlots(
            @Param("equipmentId") Long equipmentId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("blockingStatuses") Collection<BookingStatus> blockingStatuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id IN :equipmentIds
          AND b.endTime >= :rangeStart
          AND b.startTime <= :rangeEnd
        ORDER BY b.startTime ASC
    """)
    List<Booking> findBookingsInDateRangeForEquipments(
            @Param("equipmentIds") Collection<Long> equipmentIds,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

    List<Booking> findByRecurrenceGroupId(String recurrenceGroupId);

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.status IN ('COMPLETED', 'IN_USE', 'CONFIRMED')
        ORDER BY b.endTime DESC
    """)
    List<Booking> findRecentCompletedOrConfirmedBookings(@Param("equipmentId") Long equipmentId);

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.external = true
          AND b.status IN :statuses
          AND b.startTime < :periodEnd
          AND b.endTime > :periodStart
    """)
    List<Booking> findExternalBookingsInPeriod(
            @Param("equipmentId") Long equipmentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.status IN :statuses
          AND b.startTime < :rangeEnd
          AND b.endTime > :rangeStart
        ORDER BY b.startTime ASC
    """)
    List<Booking> findScheduleBookings(
            @Param("equipmentId") Long equipmentId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    List<Booking> findBySharingAgreementIdAndStartTimeAfterAndStatusIn(
            Long sharingAgreementId,
            Instant afterTime,
            Collection<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    List<Booking> findBySharingAgreementIdAndUserIdAndStartTimeAfterAndStatusIn(
            Long sharingAgreementId,
            Long userId,
            Instant afterTime,
            Collection<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    List<Booking> findBySharingAgreementIdOrderByStartTimeAsc(Long sharingAgreementId);

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.sharingAgreementId = :agreementId
          AND (:startTime IS NULL OR b.startTime >= :startTime)
          AND (:endTime IS NULL OR b.endTime <= :endTime)
        ORDER BY b.startTime ASC
    """)
    List<Booking> findAgreementBookingsInPeriod(
            @Param("agreementId") Long agreementId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
