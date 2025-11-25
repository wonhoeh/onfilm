package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.PersonRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoviePersonRequest {
    private String tempId;         // Person을 매핑하기 위한 UUID
    private Long personId;         // 기존 사람일 경우
    private PersonRole role;       // ACTOR, DIRECTOR, WRITER
    private String characterName;  // 배우만 입력
}