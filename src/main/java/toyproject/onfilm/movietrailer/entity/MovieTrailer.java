package toyproject.onfilm.movietrailer.entity;

import jakarta.persistence.*;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.movie.entity.Movie;

@Entity
public class MovieTrailer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name ="movie_id")
    private Movie movie;

    private String trailUrl;    //예고편 url
    private String trailerThumbnailUrl; //섬네일 url

    //=== 메타 데이터 ===//
}
