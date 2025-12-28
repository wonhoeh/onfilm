package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.PersonSns;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePersonRequest {
    private String name;
    private LocalDate birthDate;
    private String birthPlace;
    private String oneLineIntro;
    private String profileImageUrl;
    private List<CreatePersonSnsRequest> snsList;
    private List<String> rawTags;

    public List<PersonSns> toSnsEntities() {
        if (snsList == null || snsList.isEmpty()) return List.of();
        return snsList.stream()
                .map(req -> PersonSns.builder()
                        .type(req.getType())
                        .url(req.getUrl())
                        .build())
                .toList();
    }

    public List<String> safeRawTags() {
        return rawTags == null ? List.of() : rawTags;
    }
}
