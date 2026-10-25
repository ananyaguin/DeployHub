package com.cloudflow.repository;

import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.WorkerNode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    Page<Execution> findByDeployment_Tenant_Id(Long tenantId, Pageable pageable);

    Optional<Execution> findByPublicIdAndDeployment_Tenant_Id(UUID publicId, Long tenantId);

    Optional<Execution> findByPublicId(UUID publicId);

    boolean existsByDeployment_IdAndStatusIn(Long deploymentId, Collection<ExecutionStatus> statuses);

    Optional<Execution> findFirstByStatusOrderByCreatedAtAsc(ExecutionStatus status);

    List<Execution> findByStatus(ExecutionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Execution e
            set e.status = com.cloudflow.entity.ExecutionStatus.RUNNING,
                e.workerNode = :worker,
                e.startedAt = :startedAt,
                e.attemptCount = e.attemptCount + 1
            where e.id = :id and e.status = com.cloudflow.entity.ExecutionStatus.QUEUED
            """)
    int claimQueued(@Param("id") Long id, @Param("worker") WorkerNode worker, @Param("startedAt") Instant startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Execution e
            set e.status = :status, e.finishedAt = :finishedAt
            where e.id = :id
              and e.status = com.cloudflow.entity.ExecutionStatus.RUNNING
              and e.workerNode.id = :workerId
            """)
    int finishIfRunning(
            @Param("id") Long id,
            @Param("workerId") Long workerId,
            @Param("status") ExecutionStatus status,
            @Param("finishedAt") Instant finishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Execution e
            set e.status = com.cloudflow.entity.ExecutionStatus.QUEUED,
                e.workerNode = null,
                e.startedAt = null
            where e.id = :id
              and e.status = com.cloudflow.entity.ExecutionStatus.RUNNING
              and e.attemptCount < :maxAttempts
            """)
    int requeueIfRunning(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Execution e
            set e.status = com.cloudflow.entity.ExecutionStatus.FAILED, e.finishedAt = :finishedAt
            where e.id = :id
              and e.status = com.cloudflow.entity.ExecutionStatus.RUNNING
              and e.attemptCount >= :maxAttempts
            """)
    int failIfRunning(@Param("id") Long id, @Param("maxAttempts") int maxAttempts, @Param("finishedAt") Instant finishedAt);
}
