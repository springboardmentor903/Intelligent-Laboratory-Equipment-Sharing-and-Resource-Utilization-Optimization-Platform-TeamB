package com.campus.lab_resource_platform.maintenance.repository;

import com.campus.lab_resource_platform.maintenance.entity.WorkOrderServiceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderServiceLogRepository extends JpaRepository<WorkOrderServiceLog, Long> {

    List<WorkOrderServiceLog> findByWorkOrderIdOrderByTimestampAsc(Long workOrderId);
}
