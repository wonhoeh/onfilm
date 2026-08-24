package com.onfilm.domain.user.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;

    @Test
    void invalidAvailabilityInputsAreRejectedWithoutRepositoryQuery() {
        UserQueryService service = new UserQueryService(userRepository, storageService);

        assertThat(service.isEmailAvailable("invalid")).isFalse();
        assertThat(service.isUsernameAvailable("ab")).isFalse();
        verify(userRepository, never()).existsByEmail(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).existsByUsernameNormalized(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findMeThrowsUserNotFoundWhenCurrentUserDoesNotExist() {
        UserQueryService service = new UserQueryService(userRepository, storageService);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findMe(1L))
                .isInstanceOfSatisfying(UserNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(storageService, never()).toPublicUrl(org.mockito.ArgumentMatchers.any());
    }
}
