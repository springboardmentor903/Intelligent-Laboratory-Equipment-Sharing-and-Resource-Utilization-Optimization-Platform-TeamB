package com.campus.lab_resource_platform.sharing.repository;

import com.campus.lab_resource_platform.sharing.entity.AccessGrant;
import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, Long> {

    @EntityGraph(attributePaths = {"user", "agreement"})
    List<AccessGrant> findByAgreementId(Long agreementId);

    @EntityGraph(attributePaths = {"user", "agreement"})
    Optional<AccessGrant> findByAgreementIdAndUserId(Long agreementId, Long userId);

    Optional<AccessGrant> findByAgreementIdAndUserIdAndStatus(Long agreementId, Long userId, GrantStatus status);
}

