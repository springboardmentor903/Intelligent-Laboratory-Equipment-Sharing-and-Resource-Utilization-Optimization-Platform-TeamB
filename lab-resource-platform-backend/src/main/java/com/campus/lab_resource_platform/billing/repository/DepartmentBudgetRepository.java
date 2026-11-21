package com.campus.lab_resource_platform.billing.repository;

import com.campus.lab_resource_platform.billing.entity.DepartmentBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentBudgetRepository extends JpaRepository<DepartmentBudget, Long> {

    Optional<DepartmentBudget> findByDepartmentIdAndFiscalYear(Long departmentId, Integer fiscalYear);

    boolean existsByDepartmentIdAndFiscalYear(Long departmentId, Integer fiscalYear);

    @Query("""
        SELECT b FROM DepartmentBudget b
        WHERE b.institutionId = :institutionId
          AND (:fiscalYear IS NULL OR b.fiscalYear = :fiscalYear)
          AND (:departmentId IS NULL OR b.department.id = :departmentId)
        ORDER BY b.fiscalYear DESC, b.department.name ASC
    """)
    List<DepartmentBudget> findByInstitutionWithFilters(
            @Param("institutionId") Long institutionId,
            @Param("fiscalYear") Integer fiscalYear,
            @Param("departmentId") Long departmentId
    );
}
