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

    // cascade 없음: MovieGenre 저장이 표준 Genre 생성/저장으로 전파되지 않음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    // 표준 장르면 Genre.name, 사용자 장르면 직접 입력한 표시명
    @Column(name = "raw_text", nullable = false, length = 60)
    private String rawText;

    // 중복 제거/검색용 정규화 텍스트
    @Column(name = "normalized_text", nullable = false, length = 60)
    private String normalizedText;

    private MovieGenre(Genre genre, String rawText, String normalizedText) {
        this.genre = genre;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
    }

    public static MovieGenre createStandard(Movie movie, Genre genre) {
        Movie requiredMovie = require(movie, "movie");
        Genre requiredGenre = require(genre, "genre");
        String genreName = requireText(requiredGenre.getName(), "genre.name");
        String normalized = requireText(
                requiredGenre.getNormalized(),
                "genre.normalized"
        );

        MovieGenre movieGenre = new MovieGenre(
                requiredGenre,
                genreName,
                normalized
        );
        requiredMovie.addMovieGenre(movieGenre);

        return movieGenre;
    }

    public static MovieGenre createCustom(Movie movie, String customText) {
        Movie requiredMovie = require(movie, "movie");
        String rawText = requireText(customText, "customText");
        String normalized = TextNormalizer.textNormalizer(rawText);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("normalizedText is blank");
        }

        MovieGenre movieGenre = new MovieGenre(
                null,
                rawText,
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    // 나중에 표준 장르 매핑할 때 사용
    public void mapToGenre(Genre genre) {
        this.genre = genre;
    }
}
