package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MovieTrailer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private MovieTrailerType type;  // "MAIN", "PREVIEW"
    private String url;             // 패턴1: 파일 엔티티 없음, URL만 저장

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="movie_id")
    private Movie movie;

    private MovieTrailer(MovieTrailerType type, String url) {
        this.type = type;
        this.url = url;
    }

    void setMovie(Movie movie) {
        this.movie = movie;
    }

    public static MovieTrailer create(Movie movie, MovieTrailerType type, String url) {
        MovieTrailer trailer = new MovieTrailer(type, url);
        movie.addTrailer(trailer);
        return trailer;
    }
}