package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class CurrentPersonProviderTest {
    @Mock UserRepository userRepository;

    @Test
    void rejectsPublicIdThatDoesNotBelongToCurrentUser() {
        User user = User.create(
                UserEmail.from("user@example.com"), "encoded-password", Username.from("testuser")
        );
        Person person = user.createPerson("testuser");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        CurrentPersonProvider provider = new CurrentPersonProvider(userRepository);

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::currentUserId).thenReturn(1L);
            assertThatThrownBy(() -> provider.getRequired(person.getPublicId() + "-other"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("FORBIDDEN_PERSON_ACCESS");
        }
    }
}
