package toyproject.onfilm.domain.moviegenre;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.genre.Genre;
import toyproject.onfilm.domain.movie.Movie;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MovieGenre {

    @Id @GeneratedValue
    @Column(name = "movie_genre_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;
}
