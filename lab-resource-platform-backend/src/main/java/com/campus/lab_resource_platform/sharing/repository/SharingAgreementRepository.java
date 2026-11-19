package com.campus.lab_resource_platform.sharing.repository;

import com.campus.lab_resource_platform.sharing.entity.AgreementStatus;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import com.campus.lab_resource_platform.sharing.entity.SharingAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SharingAgreementRepository extends JpaRepository<SharingAgreement, Long> {

    @EntityGraph(attributePaths = {"equipment", "grants", "grants.user"})
    Optional<SharingAgreement> findWithGrantsById(Long id);

    @EntityGraph(attributePaths = {"equipment"})
    Page<SharingAgreement> findByOwnerInstitutionIdOrPartnerInstitutionId(
            Long ownerInstitutionId, Long partnerInstitutionId, Pageable pageable);

    List<SharingAgreement> findAllByStatus(AgreementStatus status);

    @Query("""
        SELECT sa FROM SharingAgreement sa
        JOIN sa.grants g
        WHERE sa.equipment.id = :equipmentId
          AND g.user.id = :userId
          AND sa.status = :status
          AND g.status = :grantStatus
          AND sa.endDate >= :currentDate
          AND (sa.startDate <= :currentDate OR sa.startDate IS NULL)
    """)
    List<SharingAgreement> findActiveAgreementsForUser(
            @Param("equipmentId") Long equipmentId,
            @Param("userId") Long userId,
            @Param("status") AgreementStatus status,
            @Param("grantStatus") GrantStatus grantStatus,
            @Param("currentDate") LocalDate currentDate
    );

    @Query("""
        SELECT sa FROM SharingAgreement sa
        JOIN sa.grants g
        WHERE sa.equipment.id IN :equipmentIds
          AND g.user.id = :userId
          AND sa.status = :status
          AND g.status = :grantStatus
          AND sa.endDate >= :currentDate
    """)
    List<SharingAgreement> findActiveAgreementsForUserAndEquipmentIn(
            @Param("equipmentIds") Collection<Long> equipmentIds,
            @Param("userId") Long userId,
            @Param("status") AgreementStatus status,
            @Param("grantStatus") GrantStatus grantStatus,
            @Param("currentDate") LocalDate currentDate
    );

    @Query("""
        SELECT sa FROM SharingAgreement sa
        WHERE sa.equipment.id = :equipmentId
          AND sa.partnerInstitutionId = :partnerInstitutionId
          AND sa.status = :status
          AND sa.endDate >= :currentDate
    """)
    List<SharingAgreement> findActiveAgreementsForPartner(
            @Param("equipmentId") Long equipmentId,
            @Param("partnerInstitutionId") Long partnerInstitutionId,
            @Param("status") AgreementStatus status,
            @Param("currentDate") LocalDate currentDate
    );

    @EntityGraph(attributePaths = {"equipment"})
    @Query("""
        SELECT sa FROM SharingAgreement sa
        WHERE sa.ownerInstitutionId = :institutionId OR sa.partnerInstitutionId = :institutionId
    """)
    List<SharingAgreement> findAllByInstitutionInvolved(@Param("institutionId") Long institutionId);

    @EntityGraph(attributePaths = {"equipment"})
    @Query("""
        SELECT sa FROM SharingAgreement sa
        WHERE (sa.ownerInstitutionId = :instA AND sa.partnerInstitutionId = :instB)
           OR (sa.ownerInstitutionId = :instB AND sa.partnerInstitutionId = :instA)
    """)
    List<SharingAgreement> findAgreementsBetweenInstitutions(
            @Param("instA") Long instA,
            @Param("instB") Long instB
    );

    @Query("""
        SELECT sa FROM SharingAgreement sa
        WHERE (sa.ownerInstitutionId = :institutionId OR sa.partnerInstitutionId = :institutionId)
          AND sa.status = :status
          AND sa.endDate >= :today
          AND sa.endDate <= :thirtyDaysLater
    """)
    List<SharingAgreement> findExpiringAgreements(
            @Param("institutionId") Long institutionId,
            @Param("status") AgreementStatus status,
            @Param("today") LocalDate today,
            @Param("thirtyDaysLater") LocalDate thirtyDaysLater
    );
}

