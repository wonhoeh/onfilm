package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePersonRequest {
    private String name;
    private LocalDate birthDate;
    private String birthPlace;
    private String oneLineIntro;
    private String profileImageUrl;
    private List<CreatePersonSnsRequest> snsList;
    private List<String> rawTags;

    public Person toEntity() {
        return Person.create(
                name,
                birthDate,
                birthPlace,
                oneLineIntro,
                profileImageUrl,
                toSnsEntity(),
                rawTags == null ? List.of() : rawTags
        );
    }

    private List<PersonSns> toSnsEntity() {
        if (snsList == null || snsList.isEmpty()) return List.of();

        return snsList.stream()
                .map(req -> PersonSns.builder()
                        .type(req.getType())
                        .url(req.getUrl())
                        .build())
                .toList();
    }
}