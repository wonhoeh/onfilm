package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Trailer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class TrailerPersistenceTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private TrailerRepository trailerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void trailers_preserveRegistrationOrder() {
        Movie movie = createMovie();
        movie.addTrailer("movie/1/trailer/550e8400-e29b-41d4-a716-446655440000.mp4");
        movie.addTrailer("movie/1/trailer/6ba7b810-9dad-41d1-80b4-00c04fd430c8.mp4");
        movie.addTrailer("movie/1/trailer/6ba7b811-9dad-41d1-80b4-00c04fd430c8.mp4");

        Movie savedMovie = movieRepository.saveAndFlush(movie);
        Long movieId = savedMovie.getId();
        entityManager.clear();

        Movie reloadedMovie = movieRepository.findById(movieId).orElseThrow();
        assertThat(reloadedMovie.getTrailers())
                .extracting(Trailer::getStorageKey)
                .containsExactly(
                        "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000.mp4",
                        "movie/1/trailer/6ba7b810-9dad-41d1-80b4-00c04fd430c8.mp4",
                        "movie/1/trailer/6ba7b811-9dad-41d1-80b4-00c04fd430c8.mp4"
                );
        List<?> storedSortOrders = entityManager.createNativeQuery("""
                        select sort_order
                        from trailer
                        where movie_id = :movieId
                        order by sort_order
                        """)
                .setParameter("movieId", movieId)
                .getResultList();
        assertThat(storedSortOrders)
                .extracting(value -> ((Number) value).intValue())
                .containsExactly(0, 1, 2);
    }

    @Test
    void removeTrailer_deletesOrphan() {
        Movie movie = createMovie();
        Trailer trailer = movie.addTrailer(
                "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000.mp4"
        );
        movieRepository.saveAndFlush(movie);
        Long trailerId = trailer.getId();

        movie.removeTrailer(trailer);
        movieRepository.saveAndFlush(movie);
        entityManager.clear();

        assertThat(trailerRepository.findById(trailerId)).isEmpty();
    }

    private static Movie createMovie() {
        return Movie.create(
                "Test Movie",
                120,
                2020,
                "movie-key",
                null,
                AgeRating.ALL
        );
    }
}
