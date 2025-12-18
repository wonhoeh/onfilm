package com.onfilm.domain.movie.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateMovieRequest {
    private String title;
    private Integer runtime;
    private Integer releaseYear;
    private String synopsis;
    private String movieUrl;
    private String thumbnailUrl;
}
