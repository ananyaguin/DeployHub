package com.cloudflow.repository;

import com.cloudflow.entity.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationToken t
            set t.usedAt = :now
            where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now
            """)
    int consume(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationToken t
            set t.usedAt = :now
            where t.user.id = :userId and t.usedAt is null and t.tokenHash <> :hash
            """)
    int retireOthers(@Param("userId") Long userId, @Param("hash") String hash, @Param("now") Instant now);

    Page<EmailVerificationToken> findByUser_Tenant_Id(Long tenantId, Pageable pageable);
}
