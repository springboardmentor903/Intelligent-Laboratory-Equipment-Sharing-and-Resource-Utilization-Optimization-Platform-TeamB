package com.campus.lab_resource_platform.sharing.repository;

import com.campus.lab_resource_platform.sharing.entity.Sharing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharingRepository extends JpaRepository<Sharing, Long> {

}

