package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MovieWriter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieWriterRepository extends JpaRepository<MovieWriter, Long> {
}
