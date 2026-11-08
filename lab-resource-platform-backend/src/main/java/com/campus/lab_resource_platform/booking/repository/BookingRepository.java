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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"equipment", "user"})
    Page<Booking> findByUserIdOrderByStartTimeDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.status NOT IN :excludedStatuses
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    List<Booking> findConflictingBookings(
            @Param("equipmentId") Long equipmentId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedStatuses") Collection<BookingStatus> excludedStatuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    @Query("""
        SELECT b FROM Booking b
        WHERE b.equipment.id = :equipmentId
          AND b.isExternal = true
          AND b.status IN :statuses
          AND b.startTime < :periodEnd
          AND b.endTime > :periodStart
    """)
    List<Booking> findExternalBookingsInPeriod(
            @Param("equipmentId") Long equipmentId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
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
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    List<Booking> findBySharingAgreementIdAndStartTimeAfterAndStatusIn(
            Long sharingAgreementId,
            LocalDateTime afterTime,
            Collection<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"equipment", "user"})
    List<Booking> findBySharingAgreementIdAndUserIdAndStartTimeAfterAndStatusIn(
            Long sharingAgreementId,
            Long userId,
            LocalDateTime afterTime,
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
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
