package com.cloudflow.repository;

import com.cloudflow.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Page<PasswordResetToken> findByUser_Tenant_Id(Long tenantId, Pageable pageable);
}
