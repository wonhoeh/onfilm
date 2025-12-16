package com.onfilm.domain.genre.repository;

import com.onfilm.domain.genre.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByName(String name);
    List<Genre> findByNormalizedInAndIsActiveTrue(List<String> normalizedList);
}
