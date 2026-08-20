package com.onfilm.domain.token.repository;

import com.onfilm.domain.token.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken rt
               set rt.lastUsedAt = :revokedAt,
                   rt.revokedAt = :revokedAt,
                   rt.version = rt.version + 1
             where rt.tokenHash = :tokenHash
               and rt.revokedAt is null
            """)
    int revokeActiveByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RefreshToken rt
            where rt.expiresAt <= :cutoff
               or (rt.revokedAt is not null and rt.revokedAt <= :cutoff)
            """)
    int deleteExpiredOrRevokedBefore(@Param("cutoff") Instant cutoff);
}
