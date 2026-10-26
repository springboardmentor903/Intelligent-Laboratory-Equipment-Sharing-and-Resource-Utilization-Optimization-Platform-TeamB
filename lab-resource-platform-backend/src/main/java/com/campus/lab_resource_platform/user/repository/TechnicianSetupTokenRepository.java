
package com.campus.lab_resource_platform.user.repository;

import com.campus.lab_resource_platform.user.entity.TechnicianSetupToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TechnicianSetupTokenRepository
        extends JpaRepository<TechnicianSetupToken, Long> {

    Optional<TechnicianSetupToken> findByTokenHash(String tokenHash);
}
