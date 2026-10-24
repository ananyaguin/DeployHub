package com.cloudflow.repository;

import com.cloudflow.entity.Deployment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Page<Deployment> findByTenant_Id(Long tenantId, Pageable pageable);

    Optional<Deployment> findByPublicIdAndTenant_Id(UUID publicId, Long tenantId);

    Optional<Deployment> findByTenant_IdAndSlug(Long tenantId, String slug);
}
