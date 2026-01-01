package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMovieRequest {
    private String title;
    private int runtime;
    private Integer releaseYear;
    private String movieUrl;
    private String thumbnailUrl;
    private List<String> trailerUrls;
    private List<String> rawGenreTexts;
    private AgeRating ageRating;

    private PersonRole role;
    private CastType castType;
    private String characterName;
}
