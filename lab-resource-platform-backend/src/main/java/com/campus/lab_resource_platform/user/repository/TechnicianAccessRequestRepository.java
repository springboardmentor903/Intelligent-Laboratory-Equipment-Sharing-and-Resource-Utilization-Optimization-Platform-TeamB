
package com.campus.lab_resource_platform.user.repository;

import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicianAccessRequestRepository
        extends JpaRepository<TechnicianAccessRequest, Long> {

    List<TechnicianAccessRequest>
    findByStatusOrderByCreatedAtAsc(
            TechnicianAccessRequest.RequestStatus status
    );

    boolean existsByEmailAndStatus(
            String email,
            TechnicianAccessRequest.RequestStatus status
    );
}
