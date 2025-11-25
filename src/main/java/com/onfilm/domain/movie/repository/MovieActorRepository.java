package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MovieActor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieActorRepository extends JpaRepository<MovieActor, Long> {
}
