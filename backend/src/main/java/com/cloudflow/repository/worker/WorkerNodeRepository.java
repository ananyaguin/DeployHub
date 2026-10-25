package com.cloudflow.repository.worker;

import com.cloudflow.entity.WorkerNode;
import com.cloudflow.entity.WorkerStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerNodeRepository extends JpaRepository<WorkerNode, Long> {

    Optional<WorkerNode> findByPublicId(UUID publicId);

    Optional<WorkerNode> findByName(String name);

    Page<WorkerNode> findByStatus(WorkerStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkerNode w
            set w.status = com.cloudflow.entity.WorkerStatus.ONLINE, w.lastSeenAt = :seenAt
            where w.id = :id
            """)
    int touch(@Param("id") Long id, @Param("seenAt") Instant seenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkerNode w
            set w.status = com.cloudflow.entity.WorkerStatus.OFFLINE
            where w.status = com.cloudflow.entity.WorkerStatus.ONLINE
              and (w.lastSeenAt is null or w.lastSeenAt < :cutoff)
            """)
    int markStale(@Param("cutoff") Instant cutoff);
}
