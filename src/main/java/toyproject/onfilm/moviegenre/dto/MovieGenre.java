package toyproject.onfilm.moviegenre.dto;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.genre.Genre;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.genre.entity.Genre;
import toyproject.onfilm.movie.entity.Movie;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MovieGenre {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_genre_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;
}
