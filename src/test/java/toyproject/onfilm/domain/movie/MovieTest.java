package toyproject.onfilm.domain.movie;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
public class MovieTest {

    @Autowired MovieRepository movieRepository;
/*

    @AfterEach
    public void cleanup() {
        movieRepository.deleteAll();
    }
*/

    @Test
    @Transactional
    void testMovie() {
        //given
        String title = "Avengers";
        String genre = "action";
        LocalDate releaseDate = LocalDate.of(2020, 1, 24);
        LocalDate closeDate = LocalDate.of(2020, 2, 24);

        Movie movie = movieRepository.save(Movie.builder()
                .title(title)
                .genre(genre)
                .releaseDate(releaseDate)
                .closeDate(closeDate)
                .build());

        //when
        List<Movie> movieList = movieRepository.findAll();

        //then
        Movie findMovie = movieList.get(0);
        assertThat(findMovie.getTitle()).isEqualTo(movie.getTitle());

    }
}