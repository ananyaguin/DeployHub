package com.cloudflow.repository;

import com.cloudflow.entity.NotificationPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUser_PublicIdAndUser_Tenant_Id(UUID userPublicId, Long tenantId);
}
