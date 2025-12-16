package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.movie.dto.UpdateMovieActorRequest;
import com.onfilm.domain.movie.dto.UpdateMovieRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long id;

    private String title;

    private int runtime;

    private Integer releaseYear;

    private String synopsis;

    private String movieUrl;

    private String thumbnailUrl;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoviePerson> moviePeople = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<MovieTrailer> movieTrailers = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieGenre> genres = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AgeRating ageRating;

    @ElementCollection
    private List<String> likes = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    public Movie(String title,
                 int runtime,
                 AgeRating ageRating,
                 Integer releaseYear,
                 String synopsis,
                 String movieUrl,
                 String thumbnailUrl) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseYear = releaseYear;
        this.synopsis = synopsis;
        this.movieUrl = movieUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static Movie create(
            String title,
            int runtime,
            AgeRating ageRating,
            Integer releaseYear,
            String synopsis,
            String movieUrl,
            String thumbnailUrl,
            List<String> rawGenreTexts) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("invalid title");
        }

        if (releaseYear != null && (releaseYear < 1900 || releaseYear > LocalDate.now().getYear() + 1)) {
            throw new IllegalArgumentException("invalid releaseYear");
        }

        Movie movie = Movie.builder()
                .title(title)
                .runtime(runtime)
                .ageRating(ageRating)
                .releaseYear(releaseYear)
                .synopsis(synopsis)
                .movieUrl(movieUrl)
                .thumbnailUrl(thumbnailUrl)
                .build();

        if (rawGenreTexts != null) rawGenreTexts.forEach(movie::addGenreRaw);

        return movie;
    }

    // =================================
    // 연관관계 편의 메서드
    // =================================

    public void addGenreRaw(String rawText) {
        if (rawText == null) return;

        String normalized = TextNormalizer.normalizeTag(rawText);
        if (normalized.isBlank()) return;

        // ✅ normalized 기준 중복 방지
        boolean duplicated = genres.stream()
                .anyMatch(g -> g.getNormalizedText().equals(normalized));
        if (duplicated) return;

        MovieGenre mg = MovieGenre.fromRaw(this, rawText);
        genres.add(mg);
    }


    public void addMoviePerson(MoviePerson moviePerson) {
        moviePeople.add(moviePerson);
        moviePerson.setMovie(this);
    }

    public void addTrailer(MovieTrailer movieTrailer) {
        movieTrailers.add(movieTrailer);
        movieTrailer.setMovie(this);
    }

    public void addMovieUrl(String movieUrl) {
        this.movieUrl = movieUrl;
    }

    // =================================
    // Setter
    // =================================

    public void addLike(String movieLikeId) {
        likes.add(movieLikeId);
    }

    public void removeLike(String movieLikeId) {
        likes.remove(movieLikeId);
    }


    //=== 필드 업데이트 ===//
    private void updateTitle(String title) {
        this.title = title;
    }

    private void updateRuntime(int runtime) {
        this.runtime = runtime;
    }

    private void updateReleaseDate(LocalDate releaseDate) {
        this.releaseYear = releaseDate;
    }

    private void updateSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    private void updateMovieUrl(String movieUrl) {
        this.movieUrl = movieUrl;
    }


}
