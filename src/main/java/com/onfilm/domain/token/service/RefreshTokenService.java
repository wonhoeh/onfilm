package com.onfilm.domain.token.service;

import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.exception.InvalidRefreshTokenException;
import com.onfilm.domain.common.error.exception.RefreshTokenReuseDetectedException;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.entity.TokenHashing;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    public static final Duration MAX_TTL = Duration.ofDays(90);

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashing tokenHashing;
    private final JwtProvider jwtProvider;
    private final RefreshTokenSecurityTransactionService securityTransactionService;
    private final Clock clock;

    @Transactional
    public String issue(Long userId, Duration ttl) {
        Long requiredUserId = requirePositiveUserId(userId);
        Duration requiredTtl = requireTtl(ttl);
        Instant now = clock.instant();
        String rawToken = jwtProvider.createRefreshToken();
        RefreshToken refreshToken = RefreshToken.issue(
                requiredUserId,
                tokenHashing.sha256(requireRawToken(rawToken)),
                now,
                now.plus(requiredTtl)
        );
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public RotationResult rotate(String rawToken, Duration ttl) {
        String requiredRawToken = requireRawToken(rawToken);
        Duration requiredTtl = requireTtl(ttl);
        Instant now = clock.instant();
        String hash = tokenHashing.sha256(requiredRawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked()) {
            Long userId = existing.getUserId();
            log.warn(
                    "Refresh token reuse detected; deleting all sessions for userId={}",
                    userId
            );
            securityTransactionService.deleteAllSessionsForReuse(userId);
            throw new RefreshTokenReuseDetectedException();
        }

        if (existing.isExpiredAt(now)) {
            securityTransactionService.recordExpiredUse(existing.getId(), now);
            throw new InvalidRefreshTokenException();
        }

        try {
            existing.consume(now);
            refreshTokenRepository.saveAndFlush(existing);
        } catch (OptimisticLockingFailureException exception) {
            throw new InvalidRefreshTokenException(exception);
        }

        String newRawToken = requireRawToken(jwtProvider.createRefreshToken());
        RefreshToken newToken = RefreshToken.issue(
                existing.getUserId(),
                tokenHashing.sha256(newRawToken),
                now,
                now.plus(requiredTtl)
        );
        refreshTokenRepository.save(newToken);

        return new RotationResult(existing.getUserId(), newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.revokeActiveByTokenHash(
                tokenHashing.sha256(rawToken),
                clock.instant()
        );
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        refreshTokenRepository.deleteAllByUserId(requirePositiveUserId(userId));
    }

    private static String requireRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return rawToken;
    }

    private static Long requirePositiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return userId;
    }

    private static Duration requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("refresh token ttl must be positive");
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException(
                    "refresh token ttl must not exceed " + MAX_TTL.toDays() + " days"
            );
        }
        return ttl;
    }

    public record RotationResult(Long userId, String refreshToken) {
    }
}
