
package com.campus.lab_resource_platform.user.repository;

import com.campus.lab_resource_platform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);
}
