package com.onfilm.domain.token.service;

import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.entity.TokenHashing;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.OptimisticLockException;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashing tokenHashing;
    private final JwtProvider jwtProvider;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public String issue(Long userId, Duration ttl) {
        String rawToken = jwtProvider.createRefreshToken();
        Instant expiresAt = Instant.now().plus(ttl);
        RefreshToken refreshToken = RefreshToken.issue(userId, tokenHashing.sha256(rawToken), expiresAt);
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public RotationResult rotate(String rawToken, Duration ttl) {
        String hash = tokenHashing.sha256(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        // 이미 revoked된 토큰으로 재요청 → 탈취 의심 → 해당 유저 전체 토큰 삭제
        if (existing.isRevoked()) {
            // 별도 트랜잭션으로 삭제 → 이후 예외 롤백과 무관하게 즉시 커밋
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            Long userId = existing.getUserId();
            tx.execute(status -> { refreshTokenRepository.deleteAllByUserId(userId); return null; });
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token reuse detected");
        }

        existing.markUsed();

        if (existing.isExpired()) {
            existing.revoke();
            refreshTokenRepository.save(existing);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        existing.revoke();
        try {
            refreshTokenRepository.saveAndFlush(existing);
        } catch (OptimisticLockException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Concurrent refresh token rotation detected");
        }

        String newRawToken = jwtProvider.createRefreshToken();
        Instant expiresAt = Instant.now().plus(ttl);
        RefreshToken newToken = RefreshToken.issue(existing.getUserId(), tokenHashing.sha256(newRawToken), expiresAt);
        refreshTokenRepository.save(newToken);

        return new RotationResult(existing.getUserId(), newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHashing.sha256(rawToken))
                .ifPresent(token -> {
                    token.markUsed();
                    token.revoke();
                    refreshTokenRepository.save(token);
                });
    }

    public record RotationResult(Long userId, String refreshToken) {
    }
}
