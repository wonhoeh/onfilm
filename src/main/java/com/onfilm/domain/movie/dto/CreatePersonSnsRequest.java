package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.SnsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePersonSnsRequest(
        @NotNull(message = "SNS 타입은 필수입니다.") SnsType type,
        @NotBlank(message = "SNS URL은 필수입니다.")
        @Size(max = PersonSns.URL_MAX_LENGTH, message = "SNS URL은 512자 이하여야 합니다.") String url
) {
}
