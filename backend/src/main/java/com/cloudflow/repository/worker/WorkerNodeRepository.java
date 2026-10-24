package com.cloudflow.repository.worker;

import com.cloudflow.entity.WorkerNode;
import com.cloudflow.entity.WorkerStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerNodeRepository extends JpaRepository<WorkerNode, Long> {

    Optional<WorkerNode> findByPublicId(UUID publicId);

    Page<WorkerNode> findByStatus(WorkerStatus status, Pageable pageable);
}
