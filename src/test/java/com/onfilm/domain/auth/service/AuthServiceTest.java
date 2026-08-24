package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.exception.DuplicateEmailException;
import com.onfilm.domain.common.error.exception.DuplicateUsernameException;
import com.onfilm.domain.common.error.exception.InvalidCredentialsException;
import com.onfilm.domain.token.service.RefreshTokenService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthProperties authProperties;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtProvider,
                refreshTokenService,
                authProperties
        );
    }

    @Test
    void signup_usesCanonicalIdentityAndCreatesRequiredPerson() {
        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password");

        authService.signup(new SignupRequest(
                "  User@Example.COM  ",
                "password123",
                "  TestUser  "
        ));

        verify(userRepository).existsByEmail("user@example.com");
        verify(userRepository).existsByUsernameNormalized("testuser");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getUsername()).isEqualTo("TestUser");
        assertThat(saved.getPerson()).isNotNull();
        assertThat(saved.getPerson().getUser()).isSameAs(saved);
    }

    @Test
    void signup_rejectsKnownEmailBeforeEncoding() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "user@example.com",
                "password123",
                "testuser"
        )))
                .isInstanceOf(DuplicateEmailException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void signup_translatesEmailUniqueConstraintRace() {
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(uniqueViolation("uk_users_email"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "user@example.com",
                "password123",
                "testuser"
        ))).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void signup_translatesUsernameUniqueConstraintRace() {
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(uniqueViolation("uk_users_username_normalized"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "user@example.com",
                "password123",
                "testuser"
        ))).isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void loginHidesWhetherEmailOrPasswordWasInvalid() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

        User user = User.create(
                UserEmail.from("user@example.com"),
                "encoded-password",
                Username.from("testuser")
        );
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private static DataIntegrityViolationException uniqueViolation(
            String constraintName
    ) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate",
                new SQLException("duplicate"),
                "insert into users",
                constraintName
        );
        return new DataIntegrityViolationException("duplicate user", cause);
    }
}
