package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.PersonRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMoviePersonRequest {
    private PersonRole role;       // 배우, 감독, 작가
    private CastType castType;     // 주연, 조연, 단역
    private String characterName;  // 배우만 입력
}