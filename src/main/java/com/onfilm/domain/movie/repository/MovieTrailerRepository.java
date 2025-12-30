package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Trailer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieTrailerRepository extends JpaRepository<Trailer, Long> {
}
