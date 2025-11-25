package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MoviePerson;

import java.util.List;

public interface MoviePersonQueryRepository {
    List<MoviePerson> findFilmography(Long personId);
}
