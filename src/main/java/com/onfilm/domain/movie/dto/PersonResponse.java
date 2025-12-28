package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Person;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonResponse {
    private Long id;
    private String name;
    private LocalDate birthDate;
    private String birthPlace;
    private String oneLineIntro;
    private String profileImageUrl;
    private List<PersonSnsResponse> snsList;
    private List<ProfileTagResponse> rawTags;

    public static PersonResponse from(Person person) {
        return PersonResponse.builder()
                .id(person.getId())
                .name(person.getName())
                .birthDate(person.getBirthDate())
                .birthPlace(person.getBirthPlace())
                .oneLineIntro(person.getOneLineIntro())
                .profileImageUrl(person.getProfileImageUrl())
                .snsList(person.getSnsList().stream().map(PersonSnsResponse::from).toList())
                .rawTags(person.getProfileTags().stream().map(ProfileTagResponse::from).toList())
                .build();
    }
}
