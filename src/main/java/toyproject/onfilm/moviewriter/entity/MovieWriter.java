package toyproject.onfilm.moviewriter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.writer.entity.Writer;

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
    public void addWriter(Writer writer) {
        this.writer = writer;
        writer.getFilmography().add(this);
    }

    public void addMovie(Movie movie) {
        this.movie = movie;
        movie.getMovieWriters().add(this);
    }

    //=== 생성자 메서드 ===
    public static MovieWriter createMovieWriter(Movie movie, Writer writer) {
        MovieWriter movieWriter = new MovieWriter();
        movieWriter.addWriter(writer);
        movieWriter.addMovie(movie);

        return movieWriter;
    }
}
