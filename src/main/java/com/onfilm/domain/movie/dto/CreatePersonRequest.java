package com.onfilm.domain.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePersonRequest {
    private String tempId;  // UUID, 프론트가 생성 (파일 키 매핑용)
    private String name;
    private LocalDate birthDate;
    private List<PersonSnsRequest> snsList;
}
