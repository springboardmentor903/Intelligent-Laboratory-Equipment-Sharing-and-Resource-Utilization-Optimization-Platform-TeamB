package com.campus.lab_resource_platform.booking.repository;

import com.campus.lab_resource_platform.booking.entity.BookingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingHistoryRepository extends JpaRepository<BookingHistory, Long> {

    List<BookingHistory> findByBookingIdOrderByTimestampAsc(Long bookingId);
}
