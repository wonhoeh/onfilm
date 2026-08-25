package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.AuthTokens;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.exception.InvalidCredentialsException;
import com.onfilm.domain.token.service.RefreshTokenService;
import com.onfilm.domain.user.entity.RawPasswordPolicy;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthTransactionService transactionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;

    public void signup(SignupRequest request) {
        UserEmail email = UserEmail.from(request.email());
        Username username = Username.from(request.username());
        String rawPassword = RawPasswordPolicy.validate(request.password());

        transactionService.validateSignupAvailability(email, username);
        String encodedPassword = passwordEncoder.encode(rawPassword);
        transactionService.register(
                email,
                encodedPassword,
                username
        );
    }

    public AuthTokens login(LoginRequest request) {
        UserEmail email = UserEmail.from(request.email());
        AuthTransactionService.LoginSnapshot user =
                transactionService.findLoginSnapshot(email);

        if (!passwordEncoder.matches(request.password(), user.encodedPassword())) {
            throw invalidCredentials();
        }

        String accessToken = jwtProvider.createAccessToken(
                user.userId(),
                authProperties.accessTokenTtl()
        );
        String refreshToken = refreshTokenService.issue(
                user.userId(),
                authProperties.refreshTokenTtl()
        );
        return new AuthTokens(accessToken, refreshToken);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(
                rawRefreshToken,
                authProperties.refreshTokenTtl()
        );
        String accessToken = jwtProvider.createAccessToken(
                rotation.userId(),
                authProperties.accessTokenTtl()
        );
        return new AuthTokens(accessToken, rotation.refreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException();
    }
}
