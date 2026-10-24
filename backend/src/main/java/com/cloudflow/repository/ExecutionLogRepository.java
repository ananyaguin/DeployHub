package com.cloudflow.repository;

import com.cloudflow.entity.ExecutionLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    Page<ExecutionLog> findByExecution_PublicIdAndExecution_Deployment_Tenant_Id(
            UUID executionPublicId, Long tenantId, Pageable pageable);
}
