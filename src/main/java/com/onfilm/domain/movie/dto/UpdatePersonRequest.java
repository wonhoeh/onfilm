package com.onfilm.domain.movie.dto;

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
    private String profileImageKey;
    private String profileImageUrl;
    private List<CreatePersonSnsRequest> snsList;
    private List<String> rawTags;

    public List<String> safeRawTags() {
        return rawTags == null ? List.of() : rawTags;
    }
}
