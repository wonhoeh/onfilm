package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    private List<@NotNull @Valid MovieGenreRequest> genres;
    private AgeRating ageRating;

    private PersonRole role;
    private CastType castType;
    private String characterName;
}
