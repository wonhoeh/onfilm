package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Trailer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="movie_id", nullable = false)
    private Movie movie;

    @Builder
    public Trailer(Movie movie, String url) {
        this.movie = movie;
        this.url = url;
    }

    void setMovie(Movie movie) {
        this.movie = movie;
    }
}