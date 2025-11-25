package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateMovieActorRequest {
    @NotNull(message = "배우 ID는 필수입니다.")
    private Long actorId;  //배우 ID
    @NotNull(message = "배우 역할 입력은 필수입니다.")
    private String actorRole;
}
