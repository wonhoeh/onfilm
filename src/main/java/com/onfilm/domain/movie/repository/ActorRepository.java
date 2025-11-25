package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActorRepository extends JpaRepository<Actor, Long> {
    Optional<Actor> findByName(String name);
}
