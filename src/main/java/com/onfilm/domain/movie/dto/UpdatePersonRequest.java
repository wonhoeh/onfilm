package com.onfilm.domain.movie.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.ProfileTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UpdatePersonRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = Person.NAME_MAX_LENGTH, message = "이름은 60자 이하여야 합니다.") String name,
        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.") LocalDate birthDate,
        @Size(max = Person.BIRTH_PLACE_MAX_LENGTH, message = "출생지는 80자 이하여야 합니다.") String birthPlace,
        @Size(max = Person.ONE_LINE_INTRO_MAX_LENGTH, message = "한 줄 소개는 120자 이하여야 합니다.") String oneLineIntro,
        @JsonAlias("profileImageUrl")
        @Size(max = Person.STORAGE_KEY_MAX_LENGTH, message = "프로필 이미지 키는 512자 이하여야 합니다.") String profileImageKey,
        @NotNull(message = "SNS 목록은 필수입니다.")
        List<@NotNull(message = "SNS 항목은 null일 수 없습니다.") @Valid CreatePersonSnsRequest> snsList,
        @NotNull(message = "프로필 태그 목록은 필수입니다.")
        @Size(max = Person.PROFILE_TAG_MAX_COUNT, message = "프로필 태그는 최대 20개까지 등록할 수 있습니다.")
        List<@NotBlank(message = "프로필 태그는 공백일 수 없습니다.")
                @Size(max = ProfileTag.MAX_LENGTH, message = "프로필 태그는 30자 이하여야 합니다.") String> rawTags
) {
}
