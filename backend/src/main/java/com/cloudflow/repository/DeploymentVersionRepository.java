package com.cloudflow.repository;

import com.cloudflow.entity.DeploymentVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentVersionRepository extends JpaRepository<DeploymentVersion, Long> {

    Page<DeploymentVersion> findByDeployment_Tenant_IdAndDeployment_PublicId(
            Long tenantId, UUID deploymentPublicId, Pageable pageable);

    Optional<DeploymentVersion> findByPublicIdAndDeployment_Tenant_Id(UUID publicId, Long tenantId);

    List<DeploymentVersion> findByDeployment_IdAndVersionNumberLessThanOrderByVersionNumberDesc(
            Long deploymentId, Integer versionNumber);

    @Query("select max(v.versionNumber) from DeploymentVersion v where v.deployment.id = :deploymentId")
    Integer findMaxVersionNumber(@Param("deploymentId") Long deploymentId);
}
