package com.cloudflow.repository;

import com.cloudflow.entity.EmailVerificationToken;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Page<EmailVerificationToken> findByUser_Tenant_Id(Long tenantId, Pageable pageable);
}
