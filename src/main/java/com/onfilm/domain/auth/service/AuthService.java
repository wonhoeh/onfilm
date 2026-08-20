package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.AuthTokens;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.exception.DuplicateEmailException;
import com.onfilm.domain.common.error.exception.DuplicateUsernameException;
import com.onfilm.domain.token.service.RefreshTokenService;
import com.onfilm.domain.user.entity.RawPasswordPolicy;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String EMAIL_CONSTRAINT = "UK_USERS_EMAIL";
    private static final String USERNAME_CONSTRAINT = "UK_USERS_USERNAME_NORMALIZED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties authProperties;

    @Transactional
    public void signup(SignupRequest request) {
        UserEmail email = UserEmail.from(request.email());
        Username username = Username.from(request.username());
        String rawPassword = RawPasswordPolicy.validate(request.password());

        if (userRepository.existsByEmail(email.value())) {
            throw new DuplicateEmailException();
        }
        if (userRepository.existsByUsernameNormalized(username.normalized())) {
            throw new DuplicateUsernameException();
        }

        User user = User.create(
                email,
                passwordEncoder.encode(rawPassword),
                username
        );
        user.createPerson(username.value());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateUserException(exception);
        }
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        UserEmail email = UserEmail.from(request.email());
        User user = userRepository.findByEmail(email.value())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getEncodedPassword())) {
            throw invalidCredentials();
        }

        String accessToken = jwtProvider.createAccessToken(
                user.getId(),
                authProperties.accessTokenTtl()
        );
        String refreshToken = refreshTokenService.issue(
                user.getId(),
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

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String rawUsername) {
        try {
            Username username = Username.from(rawUsername);
            return !userRepository.existsByUsernameNormalized(username.normalized());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String rawEmail) {
        try {
            UserEmail email = UserEmail.from(rawEmail);
            return !userRepository.existsByEmail(email.value());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private RuntimeException translateDuplicateUserException(
            DataIntegrityViolationException exception
    ) {
        String constraintName = findConstraintName(exception);
        if (containsConstraint(constraintName, EMAIL_CONSTRAINT)) {
            return new DuplicateEmailException();
        }
        if (containsConstraint(constraintName, USERNAME_CONSTRAINT)) {
            return new DuplicateUsernameException();
        }
        return exception;
    }

    private static String findConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean containsConstraint(String actual, String expected) {
        return actual != null
                && actual.toUpperCase(Locale.ROOT).contains(expected);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
        );
    }
}
