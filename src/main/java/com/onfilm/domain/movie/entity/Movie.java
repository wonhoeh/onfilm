package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.TextNormalizer;
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
    @Column(nullable = false, length = 10)
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
            List<String> rawGenreTexts,
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
        if (rawGenreTexts != null) rawGenreTexts.forEach(movie::addGenreRaw);


        return movie;
    }

    // ======================================================================
    // ======= 연관관계 편의 메서드: Genre =======
    // ======================================================================

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

    // ======================================================================
    // ======= 연관관계 편의 메서드: MoviePerson =======
    // ======================================================================

    public void addMoviePerson(MoviePerson moviePerson) {
        moviePerson.setMovie(this);         // 배우 필모에 this 영화 추가
        moviePeople.add(moviePerson);       // 영화에 출연한 배우에 moviePerson 추가
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
    // ======= 기본정보 변경 메서드 =======
    // ======================================================================


}
