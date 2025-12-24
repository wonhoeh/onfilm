package com.onfilm.domain.token.service;

import com.onfilm.domain.common.config.JwtProvider;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.entity.TokenHashing;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashing tokenHashing;
    private final JwtProvider jwtProvider;

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
        RefreshToken existing = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHashing.sha256(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        existing.markUsed();

        if (existing.isExpired()) {
            existing.revoke();
            refreshTokenRepository.save(existing);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        existing.revoke();
        refreshTokenRepository.save(existing);

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
