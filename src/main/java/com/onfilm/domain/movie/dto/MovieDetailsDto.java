package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.PersonRole;
import com.onfilm.domain.movie.entity.PersonSns;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class MovieDetailsDto {
    //=== Movie ===//
    private Long id;
    private String title;
    private int runtime;
    private String ageRating;

    //=== MovieTrailer ===//
    private String trailerUrl;
    private String thumbnailUrl;

    //=== MovieActor ===//
    private String name;
    private LocalDate birthDate;
    private List<PersonSns> snsList;
    private PersonRole role;

    public MovieDetailsDto(Long id, String title, int runtime,
                           String ageRating, String trailerUrl,
                           String thumbnailUrl, String name,
                           LocalDate birthDate, List<PersonSns> snsList, PersonRole role) {
        this.id = id;
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.trailerUrl = trailerUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.name = name;
        this.birthDate = birthDate;
        this.snsList = snsList;
        this.role = role;
    }
}
