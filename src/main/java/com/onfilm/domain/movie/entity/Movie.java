package com.onfilm.domain.movie.entity;

import com.onfilm.domain.genre.entity.Genre;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {
    public static final int STORAGE_KEY_MAX_LENGTH = 512;

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

    @Column(length = STORAGE_KEY_MAX_LENGTH)
    private String movieUrl;

    @Column(length = STORAGE_KEY_MAX_LENGTH)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeRating ageRating;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoviePerson> moviePeople = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
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
        this.movieUrl = requireText(movieUrl, "movieUrl", STORAGE_KEY_MAX_LENGTH);
        this.thumbnailUrl = normalizeOptionalText(thumbnailUrl, "thumbnailUrl", STORAGE_KEY_MAX_LENGTH);
    }

    public static Movie create(
            String title,
            int runtime,
            Integer releaseYear,
            String movieUrl,
            String thumbnailUrl,
            AgeRating ageRating) {

        Movie movie = new Movie(
                title,
                runtime,
                releaseYear,
                movieUrl,
                thumbnailUrl,
                ageRating
        );

        return movie;
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MovieGenre =======
    // ======================================================================

    public MovieGenre addStandardGenre(Genre genre) {
        MovieGenre movieGenre = MovieGenre.createStandard(genre);
        addMovieGenre(movieGenre);
        return movieGenre;
    }

    public MovieGenre addCustomGenre(String customText) {
        MovieGenre movieGenre = MovieGenre.createCustom(customText);
        addMovieGenre(movieGenre);
        return movieGenre;
    }

    void addMovieGenre(MovieGenre movieGenre) {
        MovieGenre requiredMovieGenre = require(movieGenre, "movieGenre");

        if (hasGenre(requiredMovieGenre.getNormalizedText())) {
            throw new IllegalArgumentException("duplicate movie genre");
        }

        requiredMovieGenre.attachMovie(this);
        genres.add(requiredMovieGenre);
    }

    public void removeMovieGenre(MovieGenre movieGenre) {
        MovieGenre requiredMovieGenre = require(movieGenre, "movieGenre");
        if (!genres.remove(requiredMovieGenre)) {
            throw new IllegalArgumentException("movieGenre does not belong to movie");
        }
        requiredMovieGenre.detachMovie(this);
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

    public MoviePerson addMoviePerson(
            Person person,
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        MoviePerson moviePerson = MoviePerson.create(
                person,
                role,
                castType,
                characterName
        );
        addMoviePerson(moviePerson);
        return moviePerson;
    }

    void addMoviePerson(MoviePerson moviePerson) {
        MoviePerson requiredMoviePerson = require(moviePerson, "moviePerson");

        if (hasSameCredit(requiredMoviePerson)) {
            throw new IllegalArgumentException("duplicate movie credit");
        }

        requiredMoviePerson.attachMovie(this);
        moviePeople.add(requiredMoviePerson);
    }

    public void removeMoviePerson(MoviePerson moviePerson) {
        MoviePerson requiredMoviePerson = require(moviePerson, "moviePerson");
        if (!moviePeople.remove(requiredMoviePerson)) {
            throw new IllegalArgumentException("moviePerson does not belong to movie");
        }
        requiredMoviePerson.detachMovie(this);
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

    public Trailer addTrailer(String storageKey) {
        Trailer trailer = Trailer.create(storageKey);
        addTrailer(trailer);
        return trailer;
    }

    void addTrailer(Trailer trailer) {
        Trailer requiredTrailer = require(trailer, "trailer");
        if (hasTrailer(requiredTrailer.getStorageKey())) {
            throw new IllegalArgumentException("duplicate trailer");
        }

        requiredTrailer.attachMovie(this);
        trailers.add(requiredTrailer);
    }

    public void removeTrailer(Trailer trailer) {
        Trailer requiredTrailer = require(trailer, "trailer");
        if (!trailers.remove(requiredTrailer)) {
            throw new IllegalArgumentException("trailer does not belong to movie");
        }
        requiredTrailer.detachMovie(this);
    }

    private boolean hasTrailer(String storageKey) {
        return trailers.stream()
                .anyMatch(trailer -> trailer.getStorageKey().equals(storageKey));
    }

    // ======================================================================
    // ======= URL 변경 메서드: MovieUrl, ThumbnailUrl =======
    // ======================================================================

    public void changeThumbnailUrl(String key) {
        this.thumbnailUrl = normalizeOptionalText(key, "thumbnailUrl", STORAGE_KEY_MAX_LENGTH);
    }

    public void changeMovieUrl(String key) {
        this.movieUrl = requireText(key, "movieUrl", STORAGE_KEY_MAX_LENGTH);
    }

    public void clearThumbnailUrl() { this.thumbnailUrl = null; }
    public void clearMovieUrl() { this.movieUrl = null; }
    public void clearTrailers() {
        for (Trailer trailer : new ArrayList<>(trailers)) {
            removeTrailer(trailer);
        }
    }

    public void changeBasicInfo(
            String title,
            int runtime,
            Integer releaseYear,
            AgeRating ageRating
    ) {
        applyBasicInfo(title, runtime, releaseYear, ageRating);
    }

    public void clearGenres() {
        for (MovieGenre genre : new ArrayList<>(genres)) {
            removeMovieGenre(genre);
        }
    }

    public List<MoviePerson> getMoviePeople() {
        return Collections.unmodifiableList(moviePeople);
    }

    public List<Trailer> getTrailers() {
        return Collections.unmodifiableList(trailers);
    }

    public List<MovieGenre> getGenres() {
        return Collections.unmodifiableList(genres);
    }

    public List<String> getLikes() {
        return Collections.unmodifiableList(likes);
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

    private static String requireText(String value, String fieldName, int maxLength) {
        String normalized = requireText(value, fieldName);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long (max " + maxLength + ")");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeOptionalText(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long (max " + maxLength + ")");
        }
        return normalized;
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
