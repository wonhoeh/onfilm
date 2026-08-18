package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.SnsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePersonSnsRequest {
    @NotNull(message = "SNS 타입은 필수입니다.")
    private SnsType type;

    @NotBlank(message = "SNS URL은 필수입니다.")
    @Size(max = 512, message = "SNS URL은 512자 이하여야 합니다.")
    private String url;
}
