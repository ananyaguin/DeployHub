package com.cloudflow.repository;

import com.cloudflow.entity.Deployment;
import com.cloudflow.entity.DeploymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Page<Deployment> findByTenant_Id(Long tenantId, Pageable pageable);

    Optional<Deployment> findByPublicIdAndTenant_Id(UUID publicId, Long tenantId);

    Optional<Deployment> findByTenant_IdAndSlug(Long tenantId, String slug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Deployment d
            set d.status = :status, d.currentVersionNumber = :version
            where d.id = :id and d.status = :expectedStatus and d.currentVersionNumber is null
            """)
    int claimFromNew(
            @Param("id") Long id,
            @Param("expectedStatus") DeploymentStatus expectedStatus,
            @Param("status") DeploymentStatus status,
            @Param("version") Integer version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Deployment d
            set d.status = :status, d.currentVersionNumber = :version
            where d.id = :id
              and d.status = :expectedStatus
              and d.currentVersionNumber = :expectedVersion
            """)
    int claimFromVersion(
            @Param("id") Long id,
            @Param("expectedStatus") DeploymentStatus expectedStatus,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("status") DeploymentStatus status,
            @Param("version") Integer version);
}
