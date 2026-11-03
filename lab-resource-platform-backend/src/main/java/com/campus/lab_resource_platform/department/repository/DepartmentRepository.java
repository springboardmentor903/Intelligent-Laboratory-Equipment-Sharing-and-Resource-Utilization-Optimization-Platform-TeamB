package com.campus.lab_resource_platform.department.repository;

import com.campus.lab_resource_platform.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByCollegeIdOrInstitutionId(Long collegeId, Long institutionId);
    List<Department> findByActiveTrue();
}
