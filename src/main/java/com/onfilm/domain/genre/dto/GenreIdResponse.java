package com.onfilm.domain.genre.dto;

import lombok.Getter;

@Getter
public class GenreIdResponse {

    private String genreId;

    public GenreIdResponse(String genreId) {
        this.genreId = genreId;
    }
}
