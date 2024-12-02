package toyproject.onfilm.domain.moviewriter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.domain.writer.Writer;

@Getter
@NoArgsConstructor
@Entity
public class MovieWriter {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id")
    private Writer writer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    //=== 연관관계 편의 메서드 ===
    public void setWriter(Writer writer) {
        this.writer = writer;
        writer.getFilmography().add(this);
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
        movie.getMovieWriters().add(this);
    }

    //=== 생성자 메서드 ===
    public static MovieWriter createMovieWriter(Writer writer, Movie movie) {
        MovieWriter movieWriter = new MovieWriter();
        movieWriter.setWriter(writer);
        movieWriter.setMovie(movie);

        return movieWriter;
    }
}
