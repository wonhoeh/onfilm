package com.onfilm.domain.person.service;


import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.service.PersonReadService;
import com.onfilm.domain.movie.service.PersonService;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
        PersonSns sns1 = PersonSns.builder()
                .type(SnsType.INSTAGRAM)
                .url("https://instagram.com/leo")
                .build();

        PersonSns sns2 = PersonSns.builder()
                .type(SnsType.TWITTER)
                .url("https://x.com/leo")
                .build();

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
        when(storageService.toPublicUrl(person.getProfileImageUrl())).thenReturn(person.getProfileImageUrl());

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
                .containsExactlyInAnyOrder(SnsType.INSTAGRAM, SnsType.TWITTER);

        assertThat(res.getSnsList())
                .extracting("url")
                .containsExactlyInAnyOrder("https://instagram.com/leo", "https://x.com/leo");

        assertThat(res.getRawTags()).hasSize(2);
        assertThat(res.getRawTags())
                .extracting("rawTag")
                .containsExactlyInAnyOrder("인셉션", "셔터아일랜드");

        verify(personRepository, times(1)).findByPublicId(person.getPublicId());
        verify(storageService, times(1)).toPublicUrl(person.getProfileImageUrl());
    }
}
