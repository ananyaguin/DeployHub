package com.cloudflow.repository;

import com.cloudflow.entity.Execution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Page<Execution> findByDeployment_Tenant_Id(Long tenantId, Pageable pageable);

    Optional<Execution> findByPublicIdAndDeployment_Tenant_Id(UUID publicId, Long tenantId);
}
