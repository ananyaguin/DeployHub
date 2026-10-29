package com.cloudflow.repository;

import com.cloudflow.entity.DeploymentEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, Long> {

    Page<DeploymentEvent> findByDeployment_Tenant_IdAndDeployment_PublicId(
            Long tenantId, UUID deploymentPublicId, Pageable pageable);

    Page<DeploymentEvent> findByDeployment_Tenant_Id(Long tenantId, Pageable pageable);
}
