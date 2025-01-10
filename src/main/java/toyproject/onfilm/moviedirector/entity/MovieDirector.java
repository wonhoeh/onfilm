package toyproject.onfilm.moviedirector.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.director.entity.Director;
import toyproject.onfilm.movie.entity.Movie;

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
    public void setDirector(Director director) {
        this.director = director;
        director.getFilmography().add(this);
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
        movie.getMovieDirectors().add(this);
    }

    //=== 생성자 메서드 ===
    public static MovieDirector createMovieDirector(Director director, Movie movie) {
        MovieDirector movieDirector = new MovieDirector();
        movieDirector.setDirector(director);
        movieDirector.setMovie(movie);

        return movieDirector;
    }


}
