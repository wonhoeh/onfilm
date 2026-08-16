package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int runtime;

    @Column(nullable = false)
    private int releaseYear;

    @Column
    private String movieUrl;

    @Column
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeRating ageRating;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoviePerson> moviePeople = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<Trailer> trailers = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieGenre> genres = new ArrayList<>();

    @ElementCollection
    private List<String> likes = new ArrayList<>();

    private Movie(
            String title,
            int runtime,
            Integer releaseYear,
            String movieUrl,
            String thumbnailUrl,
            AgeRating ageRating
    ) {
        applyBasicInfo(title, runtime, releaseYear, ageRating);
        this.movieUrl = requireText(movieUrl, "movieUrl");
        this.thumbnailUrl = normalizeOptionalText(thumbnailUrl);
    }

    public static Movie create(
            String title,
            int runtime,
            Integer releaseYear,
            String movieUrl,
            String thumbnailUrl,
            List<String> trailerUrls,
            AgeRating ageRating) {

        Movie movie = new Movie(
                title,
                runtime,
                releaseYear,
                movieUrl,
                thumbnailUrl,
                ageRating
        );

        if (trailerUrls != null) {
            trailerUrls.forEach(movie::addTrailer);
        }

        return movie;
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MovieGenre =======
    // ======================================================================

    public void addMovieGenre(MovieGenre movieGenre) {
        MovieGenre requiredMovieGenre = require(movieGenre, "movieGenre");

        if (hasGenre(requiredMovieGenre.getNormalizedText())) {
            throw new IllegalArgumentException("duplicate movie genre");
        }

        requiredMovieGenre.attachMovie(this);
        genres.add(requiredMovieGenre);
    }

    private boolean hasGenre(String normalizedText) {
        return genres.stream()
                .anyMatch(movieGenre ->
                        movieGenre.getNormalizedText().equals(normalizedText)
                );
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MoviePerson =======
    // ======================================================================

    public void addMoviePerson(MoviePerson moviePerson) {
        if (moviePerson == null) {
            throw new IllegalArgumentException("moviePerson is required");
        }

        if (hasSameCredit(moviePerson)) {
            throw new IllegalArgumentException("duplicate movie credit");
        }

        moviePerson.attachMovie(this);
        moviePeople.add(moviePerson);
    }

    private boolean hasSameCredit(MoviePerson candidate) {
        return moviePeople.stream().anyMatch(existing ->
                isSamePerson(existing.getPerson(), candidate.getPerson())
                        && existing.getRole() == candidate.getRole()
                        && existing.getCastType() == candidate.getCastType()
                        && Objects.equals(existing.getCharacterName(), candidate.getCharacterName())
        );
    }

    private boolean isSamePerson(Person existing, Person candidate) {
        if (existing == candidate) {
            return true;
        }
        if (existing.getId() == null || candidate.getId() == null) {
            return false;
        }
        return Objects.equals(existing.getId(), candidate.getId());
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MovieTrailer =======
    // ======================================================================

    public void addTrailer(String trailerUrl) {
        String normalizedUrl = requireText(trailerUrl, "trailerUrl");

        boolean duplicated = trailers.stream()
                .anyMatch(trailer -> trailer.getUrl().equals(normalizedUrl));
        if (duplicated) return;

        Trailer trailer = Trailer.builder()
                .movie(this)
                .url(normalizedUrl)
                .build();
        trailers.add(trailer);
    }

    // ======================================================================
    // ======= URL 변경 메서드: MovieUrl, ThumbnailUrl =======
    // ======================================================================

    public void changeThumbnailUrl(String key) {
        this.thumbnailUrl = normalizeOptionalText(key);
    }

    public void changeMovieUrl(String key) {
        this.movieUrl = requireText(key, "movieUrl");
    }

    public void clearThumbnailUrl() { this.thumbnailUrl = null; }
    public void clearMovieUrl() { this.movieUrl = null; }
    public void clearTrailers() { this.trailers.clear(); }

    public void changeBasicInfo(
            String title,
            int runtime,
            Integer releaseYear,
            AgeRating ageRating
    ) {
        applyBasicInfo(title, runtime, releaseYear, ageRating);
    }

    public void clearGenres() {
        this.genres.clear();
    }

    private void applyBasicInfo(
            String title,
            int runtime,
            Integer releaseYear,
            AgeRating ageRating
    ) {
        this.title = requireText(title, "title");
        this.runtime = validateRuntime(runtime);
        this.releaseYear = validateReleaseYear(releaseYear);
        this.ageRating = require(ageRating, "ageRating");
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

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int validateRuntime(int runtime) {
        if (runtime <= 0 || runtime > 1000) {
            throw new IllegalArgumentException("runtime must be between 1 and 1000");
        }
        return runtime;
    }

    private static int validateReleaseYear(Integer releaseYear) {
        int year = require(releaseYear, "releaseYear");
        int maximumYear = LocalDate.now().getYear() + 1;
        if (year < 1900 || year > maximumYear) {
            throw new IllegalArgumentException("invalid releaseYear");
        }
        return year;
    }

}
