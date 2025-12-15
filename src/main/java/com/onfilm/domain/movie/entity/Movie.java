package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.common.error.exception.ActorNotFoundException;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.dto.UpdateMovieActorRequest;
import com.onfilm.domain.movie.dto.UpdateMovieRequest;
import com.onfilm.domain.movie.repository.ActorRepository;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long id;
    private String title;
    private int runtime;
    private LocalDate releaseDate;
    private String synopsis;
    private String movieUrl;
    private String thumbnailUrl;

    // 출연진, 감독, 작가
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoviePerson> moviePeople = new ArrayList<>();

    //예고편, 섬네일
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<MovieTrailer> movieTrailers = new ArrayList<>();

    //장르
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieGenre> genres = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AgeRating ageRating;

    //영화의 좋아요
    @ElementCollection
    private List<String> likes = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    public Movie(String title, int runtime, AgeRating ageRating,
                 LocalDate releaseDate, String synopsis,
                 String movieUrl, String thumbnailUrl) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.movieUrl = movieUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static Movie create(String title, int runtime, AgeRating ageRating,
                               LocalDate releaseDate, String synopsis,
                               String movieUrl, String thumbnailUrl,
                               List<String> rawGenreTexts) {
        Movie movie = Movie.builder()
                .title(title)
                .runtime(runtime)
                .ageRating(ageRating)
                .releaseDate(releaseDate)
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

        MovieGenre mg = MovieGenre.fromRaw(this, rawText, MovieGenre.Source.USER);
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

    public void addGenre(Genre genre) {
        MovieGenre movieGenre = MovieGenre.builder()
                .movie(this)
                .genre(genre)
                .build();
        this.genres.add(movieGenre);
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

    private void updateAgeRating(String ageRating) {
        this.ageRating = ageRating;
    }

    private void updateReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    private void updateSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    private void updateMovieUrl(String movieUrl) {
        this.movieUrl = movieUrl;
    }


}
