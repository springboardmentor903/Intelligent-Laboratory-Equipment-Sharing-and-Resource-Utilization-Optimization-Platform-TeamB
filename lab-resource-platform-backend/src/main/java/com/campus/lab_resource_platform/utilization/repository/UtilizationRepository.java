package com.campus.lab_resource_platform.utilization.repository;

import com.campus.lab_resource_platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilizationRepository extends JpaRepository<Utilization, Long> {

}
