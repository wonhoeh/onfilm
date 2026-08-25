package com.onfilm.domain.auth.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.auth.security.JwtProvider;
import com.onfilm.domain.token.service.RefreshTokenService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(AuthTransactionBoundaryTest.TestConfig.class)
class AuthTransactionBoundaryTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuthProperties authProperties;

    @Test
    void signupHashesPasswordOutsideTransactionAndStoresUserInsideTransaction() {
        given(userRepository.existsByEmail("user@example.com"))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isTrue();
                    return false;
                });
        given(userRepository.existsByUsernameNormalized("testuser"))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isTrue();
                    return false;
                });
        given(passwordEncoder.encode("password123"))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isFalse();
                    return "encoded-password";
                });
        given(userRepository.saveAndFlush(any(User.class)))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isTrue();
                    return invocation.getArgument(0);
                });

        authService.signup(new SignupRequest(
                "user@example.com",
                "password123",
                "testuser"
        ));
    }

    @Test
    void loginKeepsCpuWorkOutsideAndRefreshTokenWriteInsideTransaction() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getEncodedPassword()).willReturn("encoded-password");
        given(userRepository.findByEmail("user@example.com"))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isTrue();
                    return Optional.of(user);
                });
        given(passwordEncoder.matches("password123", "encoded-password"))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isFalse();
                    return true;
                });
        given(authProperties.accessTokenTtl()).willReturn(Duration.ofMinutes(10));
        given(authProperties.refreshTokenTtl()).willReturn(Duration.ofDays(14));
        given(jwtProvider.createAccessToken(1L, Duration.ofMinutes(10)))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isFalse();
                    return "access-token";
                });
        given(refreshTokenService.issue(1L, Duration.ofDays(14)))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isTrue();
                    return "refresh-token";
                });

        authService.login(new LoginRequest("user@example.com", "password123"));
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }

        @Bean
        RefreshTokenService refreshTokenService() {
            return mock(RefreshTokenService.class);
        }

        @Bean
        AuthProperties authProperties() {
            return mock(AuthProperties.class);
        }

        @Bean
        AuthTransactionService transactionService(UserRepository userRepository) {
            return new AuthTransactionService(userRepository);
        }

        @Bean
        AuthService authService(
                AuthTransactionService transactionService,
                PasswordEncoder passwordEncoder,
                JwtProvider jwtProvider,
                RefreshTokenService refreshTokenService,
                AuthProperties authProperties
        ) {
            return new AuthService(
                    transactionService,
                    passwordEncoder,
                    jwtProvider,
                    refreshTokenService,
                    authProperties
            );
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:auth-boundary-test;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
