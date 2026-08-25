package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.AuthTokens;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.common.error.exception.DuplicateEmailException;
import com.onfilm.domain.common.error.exception.InvalidCredentialsException;
import com.onfilm.domain.token.service.RefreshTokenService;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthTransactionService transactionService;

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
                transactionService,
                passwordEncoder,
                jwtProvider,
                refreshTokenService,
                authProperties
        );
    }

    @Test
    void signupValidatesAvailabilityBeforeEncodingAndRegistersCanonicalIdentity() {
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");

        authService.signup(new SignupRequest(
                "  User@Example.COM  ",
                "password123",
                "  TestUser  "
        ));

        ArgumentCaptor<UserEmail> emailCaptor = ArgumentCaptor.forClass(UserEmail.class);
        ArgumentCaptor<Username> usernameCaptor = ArgumentCaptor.forClass(Username.class);
        verify(transactionService).register(
                emailCaptor.capture(),
                eq("encoded-password"),
                usernameCaptor.capture()
        );
        assertThat(emailCaptor.getValue().value()).isEqualTo("user@example.com");
        assertThat(usernameCaptor.getValue().value()).isEqualTo("TestUser");
        InOrder order = inOrder(transactionService, passwordEncoder);
        order.verify(transactionService).validateSignupAvailability(any(), any());
        order.verify(passwordEncoder).encode("password123");
        order.verify(transactionService).register(any(), any(), any());
    }

    @Test
    void signupRejectsKnownEmailBeforeEncoding() {
        doThrow(new DuplicateEmailException())
                .when(transactionService)
                .validateSignupAvailability(any(), any());

        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                "user@example.com",
                "password123",
                "testuser"
        ))).isInstanceOf(DuplicateEmailException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(transactionService, never()).register(any(), any(), any());
    }

    @Test
    void loginHidesMissingEmailBehindInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        given(transactionService.findLoginSnapshot(any()))
                .willThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void loginHidesWrongPasswordBehindInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        given(transactionService.findLoginSnapshot(any()))
                .willReturn(new AuthTransactionService.LoginSnapshot(1L, "encoded-password"));
        given(passwordEncoder.matches("password123", "encoded-password"))
                .willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void loginIssuesTokensAfterPasswordMatches() {
        Duration accessTtl = Duration.ofMinutes(10);
        Duration refreshTtl = Duration.ofDays(14);
        given(transactionService.findLoginSnapshot(any()))
                .willReturn(new AuthTransactionService.LoginSnapshot(1L, "encoded-password"));
        given(passwordEncoder.matches("password123", "encoded-password"))
                .willReturn(true);
        given(authProperties.accessTokenTtl()).willReturn(accessTtl);
        given(authProperties.refreshTokenTtl()).willReturn(refreshTtl);
        given(jwtProvider.createAccessToken(1L, accessTtl)).willReturn("access-token");
        given(refreshTokenService.issue(1L, refreshTtl)).willReturn("refresh-token");

        AuthTokens tokens = authService.login(
                new LoginRequest("user@example.com", "password123")
        );

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
    }
}
