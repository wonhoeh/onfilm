package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trailer_movie_storage_key",
                columnNames = {"movie_id", "storage_key"}
        )
)
public class Trailer {
    public static final int STORAGE_KEY_MAX_LENGTH = 512;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_key", nullable = false, length = STORAGE_KEY_MAX_LENGTH)
    private String storageKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    private Trailer(String storageKey) {
        this.storageKey = requireStorageKey(storageKey);
    }

    static Trailer create(String storageKey) {
        return new Trailer(storageKey);
    }

    void attachMovie(Movie movie) {
        Movie requiredMovie = require(movie, "movie");
        if (this.movie != null && this.movie != requiredMovie) {
            throw new IllegalStateException(
                    "trailer already belongs to another movie"
            );
        }
        this.movie = requiredMovie;
    }

    void detachMovie(Movie movie) {
        if (this.movie == movie) {
            this.movie = null;
        }
    }

    private static String requireStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("trailerStorageKey is required");
        }

        String trimmed = storageKey.trim();
        if (trimmed.length() > STORAGE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "trailerStorageKey is too long (max "
                            + STORAGE_KEY_MAX_LENGTH + ")"
            );
        }
        return trimmed;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
