package com.campus.lab_resource_platform.utilization.repository;

import com.campus.lab_resource_platform.utilization.entity.Utilization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UtilizationRepository extends JpaRepository<Utilization, Long> {

}

