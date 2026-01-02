package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieReadService {

    private final MoviePersonRepository moviePersonRepository;

    public List<MovieCardResponse> getPersonFilmography(String name) {
        List<MoviePerson> moviePeople = moviePersonRepository.findFilmographyByPersonName(name);
        return moviePeople.stream()
                .map(MovieCardResponse::from)
                .toList();
    }
}
