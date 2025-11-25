package com.onfilm.domain.movie.entity;

import com.onfilm.domain.genre.entity.Genre;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    @Builder
    public MovieGenre(Movie movie, Genre genre) {
        this.movie = movie;
        this.genre = genre;
    }

    void setMovie(Movie movie) {
        this.movie = movie;
    }
}
