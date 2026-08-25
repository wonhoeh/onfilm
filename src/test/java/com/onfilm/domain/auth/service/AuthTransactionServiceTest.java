package com.onfilm.domain.auth.service;

import com.onfilm.domain.common.error.exception.DuplicateEmailException;
import com.onfilm.domain.common.error.exception.DuplicateUsernameException;
import com.onfilm.domain.common.error.exception.InvalidCredentialsException;
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

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthTransactionServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AuthTransactionService(userRepository);
    }

    @Test
    void registerRechecksAvailabilityAndCreatesRequiredPerson() {
        UserEmail email = UserEmail.from("user@example.com");
        Username username = Username.from("TestUser");

        service.register(email, "encoded-password", username);

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
    void availabilityRejectsKnownEmail() {
        given(userRepository.existsByEmail("user@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.validateSignupAvailability(
                UserEmail.from("user@example.com"),
                Username.from("testuser")
        )).isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).existsByUsernameNormalized(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerTranslatesEmailUniqueConstraintRace() {
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(uniqueViolation("uk_users_email"));

        assertThatThrownBy(() -> service.register(
                UserEmail.from("user@example.com"),
                "encoded-password",
                Username.from("testuser")
        )).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void registerTranslatesUsernameUniqueConstraintRace() {
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(uniqueViolation("uk_users_username_normalized"));

        assertThatThrownBy(() -> service.register(
                UserEmail.from("user@example.com"),
                "encoded-password",
                Username.from("testuser")
        )).isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void loginSnapshotDoesNotExposeUserEntity() {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getEncodedPassword()).willReturn("encoded-password");
        given(userRepository.findByEmail("user@example.com"))
                .willReturn(Optional.of(user));

        AuthTransactionService.LoginSnapshot snapshot =
                service.findLoginSnapshot(UserEmail.from("user@example.com"));

        assertThat(snapshot.userId()).isEqualTo(1L);
        assertThat(snapshot.encodedPassword()).isEqualTo("encoded-password");
    }

    @Test
    void missingLoginEmailUsesInvalidCredentialsException() {
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLoginSnapshot(
                UserEmail.from("user@example.com")
        )).isInstanceOf(InvalidCredentialsException.class);
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
