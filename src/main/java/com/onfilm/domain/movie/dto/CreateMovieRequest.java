package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
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
    private AgeRating ageRating;
    private Integer releaseYear;
    private String synopsis;
    private List<String> rawGenreTexts;
}
