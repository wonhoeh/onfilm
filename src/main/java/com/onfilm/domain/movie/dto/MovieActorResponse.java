package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.PersonRole;
import com.onfilm.domain.movie.entity.PersonSns;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class MovieActorResponse {

    private String name;
    private LocalDate birthDate;
    private List<PersonSns> snsList;
    private PersonRole role;

    public MovieActorResponse(MoviePerson moviePerson) {
        this.name = moviePerson.getPerson().getName();
        this.birthDate = moviePerson.getPerson().getBirthDate();
        this.snsList = moviePerson.getPerson().getSnsList();
        this.role = moviePerson.getRole();
    }

    /**
     * DTO로 직접 받을 때 사용
     */
    public MovieActorResponse(MovieDetailsDto movieDetailsDto) {
        this.name = movieDetailsDto.getName();
        this.birthDate = movieDetailsDto.getBirthDate();
        this.snsList = movieDetailsDto.getSnsList();
        this.role = movieDetailsDto.getRole();
    }
}
