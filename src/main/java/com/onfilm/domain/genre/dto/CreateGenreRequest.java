package com.onfilm.domain.genre.dto;

import lombok.Getter;

@Getter
public class CreateGenreRequest {
    private String name;

    //기본 생성자
    public CreateGenreRequest() {}

    public CreateGenreRequest(String name) {
        this.name = name;
    }
}
