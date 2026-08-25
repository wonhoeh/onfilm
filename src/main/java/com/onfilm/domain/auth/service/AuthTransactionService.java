package com.onfilm.domain.auth.service;

import com.onfilm.domain.common.error.exception.DuplicateEmailException;
import com.onfilm.domain.common.error.exception.DuplicateUsernameException;
import com.onfilm.domain.common.error.exception.InvalidCredentialsException;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthTransactionService {

    private static final String EMAIL_CONSTRAINT = "UK_USERS_EMAIL";
    private static final String USERNAME_CONSTRAINT = "UK_USERS_USERNAME_NORMALIZED";

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public void validateSignupAvailability(UserEmail email, Username username) {
        validateAvailability(email, username);
    }

    @Transactional
    public void register(
            UserEmail email,
            String encodedPassword,
            Username username
    ) {
        validateAvailability(email, username);
        User user = User.create(email, encodedPassword, username);
        user.createPerson(username.value());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateUserException(exception);
        }
    }

    @Transactional(readOnly = true)
    public LoginSnapshot findLoginSnapshot(UserEmail email) {
        User user = userRepository.findByEmail(email.value())
                .orElseThrow(InvalidCredentialsException::new);
        return new LoginSnapshot(user.getId(), user.getEncodedPassword());
    }

    private void validateAvailability(UserEmail email, Username username) {
        if (userRepository.existsByEmail(email.value())) {
            throw new DuplicateEmailException();
        }
        if (userRepository.existsByUsernameNormalized(username.normalized())) {
            throw new DuplicateUsernameException();
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

    public record LoginSnapshot(Long userId, String encodedPassword) {
    }
}
