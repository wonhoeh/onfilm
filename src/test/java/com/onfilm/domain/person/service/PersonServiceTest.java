package com.onfilm.domain.person.service;

import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.CreatePersonSnsRequest;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.service.PersonReadService;
import com.onfilm.domain.movie.service.PersonService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PersonService personService; // 너의 실제 서비스 클래스명

    @InjectMocks
    private PersonReadService personReadService;

    @Test
    @DisplayName("getPerson(name): 존재하면 PersonResponse로 매핑해서 반환한다 (snsList, rawTags 포함)")
    void getPerson_success() {
        // given
        Person.SnsRegistration sns1 = new Person.SnsRegistration(
                SnsType.INSTAGRAM,
                "https://instagram.com/leo"
        );

        Person.SnsRegistration sns2 = new Person.SnsRegistration(
                SnsType.TIKTOK,
                "https://tiktok.com/@leo"
        );

        Person person = Person.create(
                "디카프리오",
                LocalDate.of(1974, 11, 11),
                "Los Angeles",
                "actor",
                "https://img.test/profile.png",
                List.of(sns1, sns2),
                List.of("인셉션", "셔터아일랜드")
        );

        when(personRepository.findByPublicId(person.getPublicId())).thenReturn(Optional.of(person));
        when(storageService.toPublicUrl(person.getProfileImageKey()))
                .thenReturn(person.getProfileImageKey());

        // when
        ProfileResponse res = personReadService.findProfileByPublicId(person.getPublicId());

        // then
        assertThat(res).isNotNull();
        assertThat(res.getName()).isEqualTo(person.getName());
        assertThat(res.getBirthDate()).isEqualTo(LocalDate.of(1974, 11, 11));
        assertThat(res.getBirthPlace()).isEqualTo("Los Angeles");
        assertThat(res.getOneLineIntro()).isEqualTo("actor");
        assertThat(res.getProfileImageUrl()).isEqualTo("https://img.test/profile.png");

        assertThat(res.getSnsList()).hasSize(2);
        assertThat(res.getSnsList())
                .extracting("type")
                .containsExactlyInAnyOrder(SnsType.INSTAGRAM, SnsType.TIKTOK);

        assertThat(res.getSnsList())
                .extracting("url")
                .containsExactlyInAnyOrder(
                        "https://instagram.com/leo",
                        "https://tiktok.com/@leo"
                );

        assertThat(res.getRawTags()).hasSize(2);
        assertThat(res.getRawTags())
                .extracting("rawTag")
                .containsExactlyInAnyOrder("인셉션", "셔터아일랜드");

        verify(personRepository, times(1)).findByPublicId(person.getPublicId());
        verify(storageService, times(1)).toPublicUrl(person.getProfileImageKey());
    }

    @Test
    @DisplayName("프로필 초기화는 회원가입 시 생성된 Person을 새 객체로 교체하지 않는다")
    void initializePersonProfile_updatesRequiredExistingPerson() {
        User user = User.create(
                UserEmail.from("user@example.com"),
                "encoded-password",
                Username.from("testuser")
        );
        Person original = user.createPerson("testuser");
        CreatePersonRequest request = new CreatePersonRequest(
                "  변경된 이름  ",
                LocalDate.of(1990, 1, 1),
                "  서울  ",
                "  소개  ",
                "profile/user/avatar.png",
                List.of(new CreatePersonSnsRequest(
                        SnsType.INSTAGRAM,
                        "instagram.com/testuser"
                )),
                List.of("배우")
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::currentUserId).thenReturn(1L);

            personService.initializePersonProfile(request);
        }

        assertThat(user.getPerson()).isSameAs(original);
        assertThat(original.getName()).isEqualTo("변경된 이름");
        assertThat(original.getBirthPlace()).isEqualTo("서울");
        assertThat(original.getSnsList()).hasSize(1);
        assertThat(original.getProfileTags())
                .extracting(tag -> tag.getRawText())
                .containsExactly("배우");
        verify(userRepository, never()).save(any(User.class));
    }
}
