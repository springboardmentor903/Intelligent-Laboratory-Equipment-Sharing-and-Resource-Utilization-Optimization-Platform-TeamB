package com.campus.lab_resource_platform.sharing.repository;

import com.campus.lab_resource_platform.sharing.entity.SharingListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SharingListingRepository extends JpaRepository<SharingListing, Long>, JpaSpecificationExecutor<SharingListing> {

    @EntityGraph(attributePaths = {"equipment", "equipment.category"})
    Optional<SharingListing> findByEquipmentId(Long equipmentId);

    boolean existsByEquipmentId(Long equipmentId);

    @EntityGraph(attributePaths = {"equipment", "equipment.category"})
    @Query("""
        SELECT sl FROM SharingListing sl
        JOIN sl.equipment e
        LEFT JOIN e.category c
        WHERE sl.active = true
          AND (:excludeInstitutionId IS NULL OR (
                (sl.ownerInstitutionId IS NULL OR sl.ownerInstitutionId != :excludeInstitutionId)
                AND (e.collegeId IS NULL OR e.collegeId != :excludeInstitutionId)
                AND (e.institutionId IS NULL OR e.institutionId != :excludeInstitutionId)
          ))
          AND (:filterInstitutionId IS NULL OR sl.ownerInstitutionId = :filterInstitutionId OR e.collegeId = :filterInstitutionId)
          AND (:categoryId IS NULL OR (c IS NOT NULL AND c.id = :categoryId))
          AND (:searchPattern IS NULL OR (
                LOWER(e.name) LIKE :searchPattern
                OR LOWER(e.assetTag) LIKE :searchPattern
                OR LOWER(e.description) LIKE :searchPattern
                OR LOWER(sl.sharingTerms) LIKE :searchPattern
          ))
    """)
    Page<SharingListing> discoverListings(
            @Param("excludeInstitutionId") Long excludeInstitutionId,
            @Param("filterInstitutionId") Long filterInstitutionId,
            @Param("categoryId") Long categoryId,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}

