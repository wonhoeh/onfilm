package com.onfilm.domain.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    @Valid
    private List<@NotNull CreatePersonSnsRequest> snsList;
    private List<String> rawTags;
}
