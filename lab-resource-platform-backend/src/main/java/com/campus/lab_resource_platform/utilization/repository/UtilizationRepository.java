package com.campus.lab_resource_platform.utilization.repository;

import com.campus.lab_resource_platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
