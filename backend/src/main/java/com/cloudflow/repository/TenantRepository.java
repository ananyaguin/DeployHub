package com.cloudflow.repository;

import com.cloudflow.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByPublicId(UUID publicId);

    Optional<Tenant> findBySlug(String slug);
}
