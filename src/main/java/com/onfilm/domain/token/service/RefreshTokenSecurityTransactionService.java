package com.onfilm.domain.token.service;

import com.onfilm.domain.common.error.exception.InvalidRefreshTokenException;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenSecurityTransactionService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExpiredUse(Long tokenId, Instant usedAt) {
        RefreshToken token = refreshTokenRepository.findById(tokenId)
                .orElseThrow(InvalidRefreshTokenException::new);
        token.revoke(usedAt);
        refreshTokenRepository.saveAndFlush(token);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAllSessionsForReuse(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        refreshTokenRepository.flush();
    }
}
