package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.common.config.JwtProvider;
import com.onfilm.domain.token.service.RefreshTokenService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        String hashed = passwordEncoder.encode(request.password());
        userRepository.save(User.create(request.email(), hashed, request.username()));
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), authProperties.accessTokenTtl());
        String refreshToken = refreshTokenService.issue(user.getId(), authProperties.refreshTokenTtl());
        return new AuthTokens(accessToken, refreshToken);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken, authProperties.refreshTokenTtl());
        String accessToken = jwtProvider.createAccessToken(rotation.userId(), authProperties.accessTokenTtl());
        return new AuthTokens(accessToken, rotation.refreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
