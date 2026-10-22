package com.campus.lab_resource_platform.notification.repository;

import com.campus.lab_resource_platform.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

}
