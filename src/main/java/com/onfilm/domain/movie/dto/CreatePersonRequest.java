package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.ProfileTag;
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
    private List<CreatePersonSnsRequest> snsList;
    private List<String> rawTags;
}
