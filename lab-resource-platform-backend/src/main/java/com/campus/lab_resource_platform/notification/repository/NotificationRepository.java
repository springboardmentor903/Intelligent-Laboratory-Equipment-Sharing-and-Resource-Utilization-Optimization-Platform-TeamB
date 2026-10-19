package com.campus.lab_resource_platform.notification.repository;

import com.campus.lab_resource_platform.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

}

