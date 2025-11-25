package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
public class MovieDirector {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "director_id")
    private Director director;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    //=== 연관 관계 편의 메서드 ===
    public void addDirector(Director director) {
        this.director = director;
    }

    public void addMovie(Movie movie) {
        this.movie = movie;
    }

    //=== 생성자 메서드 ===
    public static MovieDirector createMovieDirector(Movie movie, Director director) {
        MovieDirector movieDirector = new MovieDirector();
        movieDirector.addMovie(movie);
        movieDirector.addDirector(director);

        return movieDirector;
    }
}
