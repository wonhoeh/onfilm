package com.onfilm.domain.common.config;

import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DevDataInitializerTest {

    private final UserRepository userRepository = org.mockito.Mockito.mock(
            UserRepository.class
    );
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(
            PasswordEncoder.class
    );
    private final DevDataInitializer initializer = new DevDataInitializer(
            userRepository,
            passwordEncoder
    );

    @Test
    void createsOneDevelopmentFixtureThroughDomainMethods() throws Exception {
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(passwordEncoder.encode("test1234")).willReturn("encoded-test-password");

        initializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User user = captor.getValue();

        assertThat(user.getEmail()).isEqualTo("test@test.com");
        assertThat(user.getUsername()).isEqualTo("testactor");
        assertThat(user.getPerson().getName()).isEqualTo("테스트 배우");
        assertThat(user.getPerson().getSnsList()).hasSize(2);
        assertThat(user.getPerson().getProfileTags()).hasSize(3);
        assertThat(user.getPerson().getGalleryItems()).hasSize(3);
        assertThat(user.getPerson().getStoryboardProjects())
                .hasSize(10)
                .allSatisfy(project -> assertThat(project.getScenes()).hasSize(3));
    }

    @Test
    void existingDevelopmentUserPreventsDuplicateFixture() throws Exception {
        given(userRepository.existsByEmail("test@test.com")).willReturn(true);

        initializer.run(null);

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
