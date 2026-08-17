package com.onfilm.domain.genre.repository;

import com.onfilm.domain.genre.entity.Genre;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByName(String name);

    @Query("""
            SELECT g
            FROM Genre g
            WHERE g.isActive = true
              AND g.normalized LIKE CONCAT(:prefix, '%')
            ORDER BY
                CASE WHEN g.normalized = :prefix THEN 0 ELSE 1 END,
                g.normalized ASC
            """)
    List<Genre> findActiveByPrefix(
            @Param("prefix") String prefix,
            Pageable pageable
    );

    @Query("""
            SELECT g
            FROM Genre g
            WHERE g.isActive = true
              AND g.id IN :ids
            """)
    List<Genre> findActiveByIds(@Param("ids") List<Long> ids);

    @Query("""
            SELECT g
            FROM Genre g
            WHERE g.isActive = true
              AND g.normalized IN :normalizedValues
            """)
    List<Genre> findActiveByNormalizedValues(
            @Param("normalizedValues") List<String> normalizedValues
    );
}
