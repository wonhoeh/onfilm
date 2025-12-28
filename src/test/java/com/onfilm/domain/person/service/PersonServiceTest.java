package com.onfilm.domain.person.service;


import com.onfilm.domain.movie.dto.PersonResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.ProfileTag;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.service.PersonService;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
public class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private PersonService personService; // 너의 실제 서비스 클래스명

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

        when(personRepository.findByName(person.getName())).thenReturn(Optional.of(person));

        // when
        PersonResponse res = personService.getPersonByUsername(person.getName());

        // then
        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(person.getId());
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

        verify(personRepository, times(1)).findByName(person.getName());
    }
}
