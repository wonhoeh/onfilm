package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MovieDirector;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieDirectorRepository extends JpaRepository<MovieDirector, Long> {
}
