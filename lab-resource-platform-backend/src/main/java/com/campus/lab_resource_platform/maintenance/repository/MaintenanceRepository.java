package com.campus.lab_resource_platform.maintenance.repository;

import com.campus.lab_resource_platform.maintenance.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

}

