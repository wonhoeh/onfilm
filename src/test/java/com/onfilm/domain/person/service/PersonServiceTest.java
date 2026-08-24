package com.onfilm.domain.person.service;

import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.CreatePersonSnsRequest;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.service.CurrentPersonProvider;
import com.onfilm.domain.movie.service.PersonCommandService;
import com.onfilm.domain.movie.service.PersonQueryService;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PersonCommandQueryServiceTest {
    @Mock PersonRepository personRepository;
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @Mock CurrentPersonProvider currentPersonProvider;
    @InjectMocks PersonQueryService personQueryService;
    @InjectMocks PersonCommandService personCommandService;

    @Test
    void query_mapsProfileAndPublicUrl() {
        Person person = person();
        given(personRepository.findByPublicId(person.getPublicId())).willReturn(Optional.of(person));
        given(storageService.toPublicUrl(person.getProfileImageKey())).willReturn("https://cdn/profile.png");

        ProfileResponse result = personQueryService.findProfileByPublicId(person.getPublicId());

        assertThat(result.name()).isEqualTo("디카프리오");
        assertThat(result.profileImageUrl()).isEqualTo("https://cdn/profile.png");
        assertThat(result.snsList()).hasSize(1);
        assertThat(result.rawTags()).hasSize(1);
    }

    @Test
    void command_updatesExistingPersonInsteadOfReplacingIt() {
        Person person = person();
        given(currentPersonProvider.getRequired()).willReturn(person);
        CreatePersonRequest request = new CreatePersonRequest(
                "  변경된 이름  ", LocalDate.of(1990, 1, 1), "  서울  ", "  소개  ",
                "profile/new.png",
                List.of(new CreatePersonSnsRequest(SnsType.INSTAGRAM, "instagram.com/test")),
                List.of("배우")
        );

        Long result = personCommandService.initializeProfile(request);

        assertThat(result).isEqualTo(person.getId());
        assertThat(person.getName()).isEqualTo("변경된 이름");
        assertThat(person.getBirthPlace()).isEqualTo("서울");
        assertThat(person.getSnsList()).hasSize(1);
    }

    private static Person person() {
        return Person.create(
                "디카프리오", LocalDate.of(1974, 11, 11), "Los Angeles", "actor",
                "profile/key.png",
                List.of(new Person.SnsRegistration(SnsType.INSTAGRAM, "instagram.com/leo")),
                List.of("배우")
        );
    }
}
