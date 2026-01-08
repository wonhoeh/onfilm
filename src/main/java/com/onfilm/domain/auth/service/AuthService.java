package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.AuthTokens;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.common.config.JwtProvider;
import com.onfilm.domain.movie.entity.Person;
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

        User user = User.create(request.email(), hashed, request.username());

        Person person = Person.create(
                request.username(),
                null, null, null,
                null, null, null
        );
        user.attachPerson(person);

        userRepository.save(user);
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

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        if (username == null) return false;

        String v = username.trim();
        // 프론트 정규식과 동일하게 맞추면 더 깔끔함
        if (!v.matches("^[a-zA-Z0-9_-]{3,20}$")) return false;

        return !userRepository.existsByUsername(v);
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        if (email == null) return false;

        String v = email.trim();
        // 아주 기본적인 이메일 형식 체크 (프론트와 같은 역할)
        if (!v.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return false;

        return !userRepository.existsByEmail(v);
    }

}
