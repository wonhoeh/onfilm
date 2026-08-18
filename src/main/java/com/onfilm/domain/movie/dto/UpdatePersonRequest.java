package com.onfilm.domain.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Valid
    private List<@NotNull CreatePersonSnsRequest> snsList;
    @Size(max = 20, message = "프로필 태그는 최대 20개까지 등록할 수 있습니다.")
    private List<
            @NotBlank(message = "프로필 태그는 공백일 수 없습니다.")
            @Size(max = 30, message = "프로필 태그는 30자 이하여야 합니다.")
            String
            > rawTags;
}
