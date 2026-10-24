package com.cloudflow.repository;

import com.cloudflow.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Page<User> findByTenant_Id(Long tenantId, Pageable pageable);

    Optional<User> findByPublicIdAndTenant_Id(UUID publicId, Long tenantId);

    Optional<User> findByTenant_IdAndEmail(Long tenantId, String email);
}
