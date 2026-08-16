package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.util.TextNormalizer;
import com.onfilm.domain.genre.entity.Genre;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "movie_genre",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_movie_genre_normalized",
                columnNames = {"movie_id", "normalized_text"}
        ),
        indexes = {
                @Index(name = "idx_movie_genre_movie", columnList = "movie_id"),
                @Index(name = "idx_movie_genre_genre", columnList = "genre_id"),
                @Index(name = "idx_movie_genre_norm", columnList = "normalized_text")
        })
public class MovieGenre {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    // ✅ 사용자가 입력한 원문 (무조건 저장)
    @Column(name = "raw_text", nullable = false, length = 60)
    private String rawText;

    // ✅ 중복 제거/검색용 정규화 텍스트
    @Column(name = "normalized_text", nullable = false, length = 60)
    private String normalizedText;

    private MovieGenre(Genre genre, String rawText, String normalizedText) {
        this.genre = genre;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
    }

    public static MovieGenre create(Movie movie, Genre matchedGenreOrNull, String rawText) {
        Movie requiredMovie = require(movie, "movie");
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("rawText is required");
        }

        String cleanedRaw = rawText.trim();
        String normalized = TextNormalizer.textNormalizer(cleanedRaw);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("normalizedText is blank");
        }

        MovieGenre movieGenre = new MovieGenre(
                matchedGenreOrNull,
                cleanedRaw,
                normalized
        );
        requiredMovie.addMovieGenre(movieGenre);

        return movieGenre;
    }

    void attachMovie(Movie movie) {
        Movie requiredMovie = require(movie, "movie");
        if (this.movie != null && this.movie != requiredMovie) {
            throw new IllegalStateException("movieGenre already belongs to another movie");
        }
        this.movie = requiredMovie;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    // 나중에 표준 장르 매핑할 때 사용
    public void mapToGenre(Genre genre) {
        this.genre = genre;
    }
}
