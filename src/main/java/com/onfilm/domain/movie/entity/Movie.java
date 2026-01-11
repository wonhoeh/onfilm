package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int runtime;

    @Column(nullable = false)
    private Integer releaseYear;

    @Column(nullable = false)
    private String movieUrl;

    @Column(nullable = true)
    private String thumbnailUrl;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoviePerson> moviePeople = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<Trailer> trailers = new ArrayList<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieGenre> genres = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeRating ageRating;

    @ElementCollection
    private List<String> likes = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    public Movie(String title,
                 int runtime,
                 Integer releaseYear,
                 String movieUrl,
                 String thumbnailUrl,
                 AgeRating ageRating) {

        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseYear = releaseYear;
        this.movieUrl = movieUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static Movie create(
            String title,
            int runtime,
            Integer releaseYear,
            String movieUrl,
            String thumbnailUrl,
            List<String> trailerUrls,
            AgeRating ageRating) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("invalid title");
        }

        if (releaseYear != null && (releaseYear < 1900 || releaseYear > LocalDate.now().getYear() + 1)) {
            throw new IllegalArgumentException("invalid releaseYear");
        }

        Movie movie = Movie.builder()
                .title(title)
                .runtime(runtime)
                .releaseYear(releaseYear)
                .movieUrl(movieUrl)
                .thumbnailUrl(thumbnailUrl)
                .ageRating(ageRating)
                .build();

        if (trailerUrls != null) trailerUrls.forEach(movie::addTrailer);

        return movie;
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MovieGenre =======
    // ======================================================================

    public void addMovieGenre(MovieGenre mg) {
        if (mg == null) return;

        // ✅ normalized 기준 중복 방지(정책 통일)
        boolean duplicated = genres.stream()
                .anyMatch(g -> g.getNormalizedText().equals(mg.getNormalizedText()));
        if (duplicated) return; // 또는 throw (정책에 따라)

        genres.add(mg); // mg.movie는 create에서 이미 세팅됨
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MoviePerson =======
    // ======================================================================

    public void addMoviePerson(MoviePerson moviePerson) {
        if (moviePerson == null) return;

        boolean duplicated = moviePeople.stream().anyMatch(x ->
                x.getPerson().getId().equals(moviePerson.getId()) &&
                        (x.getRole() == moviePerson.getRole()) &&
                        (x.getCastType() == moviePerson.getCastType()) &&
                        Objects.equals(x.getCharacterName(), moviePerson.getCharacterName())
        );
        if (duplicated) return; // or throw new XXX

        moviePerson.attachMovie(this);         // 배우 필모에 this 영화 추가
        moviePeople.add(moviePerson);          // 영화에 출연한 배우에 moviePerson 추가
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: MovieTrailer =======
    // ======================================================================

    public void addTrailer(String trailerUrl) {
        if (trailerUrl == null) return;

        // 중복 방지
        boolean duplicated = trailers.stream()
                        .anyMatch(t -> t.getUrl().equals(trailerUrl));
        if (duplicated) return;

        Trailer trailer = Trailer.builder()
                .movie(this)
                .url(trailerUrl)
                .build();
        trailers.add(trailer);
    }

    // ======================================================================
    // ======= URL 변경 메서드: MovieUrl, ThumbnailUrl =======
    // ======================================================================


    public void changeThumbnailUrl(String key) { this.thumbnailUrl = key; }
    public void changeMovieUrl(String key) { this.movieUrl = key; }

    public void clearThumbnailUrl() { this.thumbnailUrl = null; }
    public void clearMovieUrl() { this.movieUrl = null; }
    public void clearTrailers() { this.trailers.clear(); }

    public void updateBasic(
            String title,
            int runtime,
            Integer releaseYear,
            AgeRating ageRating
    ) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("invalid title");
        }
        if (releaseYear != null && (releaseYear < 1900 || releaseYear > LocalDate.now().getYear() + 1)) {
            throw new IllegalArgumentException("invalid releaseYear");
        }
        if (ageRating == null) {
            throw new IllegalArgumentException("invalid ageRating");
        }
        this.title = title;
        this.runtime = runtime;
        this.releaseYear = releaseYear;
        this.ageRating = ageRating;
    }

    public void clearGenres() {
        this.genres.clear();
    }



    // ======================================================================
    // ======= 기본정보 변경 메서드 =======
    // ======================================================================


}
