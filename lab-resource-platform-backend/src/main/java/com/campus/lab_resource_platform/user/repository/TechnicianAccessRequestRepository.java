package com.campus.lab_resource_platform.user.repository;

import com.campus.lab_resource_platform.user.entity.TechnicianAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TechnicianAccessRequestRepository
        extends JpaRepository<TechnicianAccessRequest, Long> {

    List<TechnicianAccessRequest> findByStatusOrderByCreatedAtAsc(
            TechnicianAccessRequest.RequestStatus status
    );

    List<TechnicianAccessRequest> findByStatusInOrderByCreatedAtDesc(
            Collection<TechnicianAccessRequest.RequestStatus> statuses
    );

    List<TechnicianAccessRequest> findByEmailIgnoreCaseOrderByCreatedAtDesc(
            String email
    );

    boolean existsByEmailAndStatus(
            String email,
            TechnicianAccessRequest.RequestStatus status
    );
}
