package com.onfilm.domain.genre.dto;

import lombok.Getter;

@Getter
public class GenreDto {
    private String id;
    private String name;

    public GenreDto(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
