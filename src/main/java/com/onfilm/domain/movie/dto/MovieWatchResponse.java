package com.onfilm.domain.movie.dto;

import lombok.Getter;

@Getter
public class MovieWatchResponse {
    private String title;       // 영화 제목
    private String movieUrl;    // S3의 영화 재생 URL

    public MovieWatchResponse(String title, String movieUrl) {
        this.title = title;
        this.movieUrl = movieUrl;
    }
}
