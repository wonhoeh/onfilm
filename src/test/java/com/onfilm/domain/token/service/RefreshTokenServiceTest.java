package com.onfilm.domain.token.service;

import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.exception.InvalidRefreshTokenException;
import com.onfilm.domain.common.error.exception.RefreshTokenReuseDetectedException;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.entity.TokenHashing;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(14);
    private static final String OLD_RAW_TOKEN = "old-raw-token";
    private static final String NEW_RAW_TOKEN = "new-raw-token";
    private static final String OLD_HASH = "o".repeat(RefreshToken.TOKEN_HASH_LENGTH);
    private static final String NEW_HASH = "n".repeat(RefreshToken.TOKEN_HASH_LENGTH);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenHashing tokenHashing;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenSecurityTransactionService securityTransactionService;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                tokenHashing,
                jwtProvider,
                securityTransactionService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void issue_usesSingleClockInstantAndValidatedTtl() {
        when(jwtProvider.createRefreshToken()).thenReturn(NEW_RAW_TOKEN);
        when(tokenHashing.sha256(NEW_RAW_TOKEN)).thenReturn(NEW_HASH);

        String issued = refreshTokenService.issue(1L, TTL);

        assertThat(issued).isEqualTo(NEW_RAW_TOKEN);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void issue_rejectsInvalidUserAndTtlBeforeGeneratingToken() {
        assertThatThrownBy(() -> refreshTokenService.issue(0L, TTL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
        assertThatThrownBy(() -> refreshTokenService.issue(1L, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refresh token ttl must be positive");
        assertThatThrownBy(() -> refreshTokenService.issue(
                1L,
                RefreshTokenService.MAX_TTL.plusDays(1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");

        verify(jwtProvider, never()).createRefreshToken();
    }

    @Test
    void rotate_recordsExpiredUseInIndependentServiceThenRejects() {
        RefreshToken expired = RefreshToken.issue(
                1L,
                OLD_HASH,
                NOW.minus(Duration.ofDays(2)),
                NOW
        );
        setUpLookup(expired);

        assertThatThrownBy(() -> refreshTokenService.rotate(OLD_RAW_TOKEN, TTL))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(securityTransactionService).recordExpiredUse(expired.getId(), NOW);
        verify(refreshTokenRepository, never()).saveAndFlush(expired);
        verify(jwtProvider, never()).createRefreshToken();
    }

    @Test
    void rotate_detectsReuseAndDeletesAllUserSessions() {
        RefreshToken revoked = activeToken();
        revoked.revoke(NOW.minusSeconds(1));
        setUpLookup(revoked);

        assertThatThrownBy(() -> refreshTokenService.rotate(OLD_RAW_TOKEN, TTL))
                .isInstanceOf(RefreshTokenReuseDetectedException.class);

        verify(securityTransactionService).deleteAllSessionsForReuse(1L);
        verify(jwtProvider, never()).createRefreshToken();
    }

    @Test
    void rotate_translatesSpringOptimisticLockFailure() {
        RefreshToken active = activeToken();
        setUpLookup(active);
        when(refreshTokenRepository.saveAndFlush(active))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> refreshTokenService.rotate(OLD_RAW_TOKEN, TTL))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void rotate_consumesOldTokenAndIssuesReplacement() {
        RefreshToken active = activeToken();
        setUpLookup(active);
        when(jwtProvider.createRefreshToken()).thenReturn(NEW_RAW_TOKEN);
        when(tokenHashing.sha256(NEW_RAW_TOKEN)).thenReturn(NEW_HASH);

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(
                OLD_RAW_TOKEN,
                TTL
        );

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.refreshToken()).isEqualTo(NEW_RAW_TOKEN);
        assertThat(active.getRevokedAt()).isEqualTo(NOW);
        assertThat(active.getLastUsedAt()).isEqualTo(NOW);
        verify(refreshTokenRepository).saveAndFlush(active);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_rejectsMissingRawTokenBeforeHashing() {
        assertThatThrownBy(() -> refreshTokenService.rotate(" ", TTL))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokenHashing, never()).sha256(anyString());
    }

    @Test
    void revoke_isIdempotentBulkUpdateAndBlankTokenIsIgnored() {
        when(tokenHashing.sha256(OLD_RAW_TOKEN)).thenReturn(OLD_HASH);

        refreshTokenService.revoke(OLD_RAW_TOKEN);
        refreshTokenService.revoke(" ");

        verify(refreshTokenRepository).revokeActiveByTokenHash(OLD_HASH, NOW);
        verify(tokenHashing).sha256(OLD_RAW_TOKEN);
    }

    @Test
    void deleteAllForUser_validatesIdentityAndDeletesTokens() {
        refreshTokenService.deleteAllForUser(1L);

        verify(refreshTokenRepository).deleteAllByUserId(1L);
        assertThatThrownBy(() -> refreshTokenService.deleteAllForUser(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
    }

    private RefreshToken activeToken() {
        return RefreshToken.issue(
                1L,
                OLD_HASH,
                NOW.minusSeconds(10),
                NOW.plus(TTL)
        );
    }

    private void setUpLookup(RefreshToken token) {
        when(tokenHashing.sha256(OLD_RAW_TOKEN)).thenReturn(OLD_HASH);
        when(refreshTokenRepository.findByTokenHash(OLD_HASH))
                .thenReturn(Optional.of(token));
    }
}
