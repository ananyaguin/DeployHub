package com.cloudflow.repository;

import com.cloudflow.entity.NotificationChannel;
import com.cloudflow.entity.NotificationDelivery;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Page<NotificationDelivery> findByUser_Tenant_IdAndUser_PublicId(Long tenantId, UUID userPublicId, Pageable pageable);

    boolean existsByUser_IdAndExecution_IdAndChannel(Long userId, Long executionId, NotificationChannel channel);
}
