package toyproject.onfilm.movietrailer.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.movie.entity.Movie;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MovieTrailer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name ="movie_id")
    private Movie movie;

    private String trailUrl;    //예고편 url
    private String thumbnailUrl; //섬네일 url

    //=== 메타 데이터 ===//

    //=== 연관 관계 메서드 ===//
    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    @Builder
    public MovieTrailer(String trailerUrl, String thumbnailUrl) {
        this.trailUrl =trailerUrl;
        this.thumbnailUrl = thumbnailUrl;
    }
}
