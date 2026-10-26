package com.campus.lab_resource_platform.booking.repository;

import com.campus.lab_resource_platform.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

}
