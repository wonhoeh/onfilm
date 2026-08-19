package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.event.StorageFilesDeleteEvent;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PersonReadServiceMovieFileTest {

    private static final String TRAILER_KEY =
            "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000.mp4";

    @Mock
    private PersonRepository personRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MoviePersonRepository moviePersonRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private StorageKeyPolicy storageKeyPolicy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PersonReadService personReadService;

    @Test
    void addMovieTrailer_validatesStorageKeyBeforeAttaching() {
        Movie movie = createMovie();
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));

        personReadService.addMovieTrailer(1L, TRAILER_KEY);

        verify(storageKeyPolicy).validateMovieTrailerKey(1L, TRAILER_KEY);
        assertThat(movie.getTrailers())
                .extracting(Trailer::getStorageKey)
                .containsExactly(TRAILER_KEY);
    }

    @Test
    void deleteMovieFiles_clearsEntityAndPublishesCollectedKeys() {
        Movie movie = createMovie();
        movie.changeThumbnailUrl("thumbnail-key");
        Trailer trailer = movie.addTrailer(TRAILER_KEY);
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));

        personReadService.deleteMovieFiles(1L);

        assertThat(movie.getMovieUrl()).isNull();
        assertThat(movie.getThumbnailUrl()).isNull();
        assertThat(movie.getTrailers()).isEmpty();
        assertThat(trailer.getMovie()).isNull();

        ArgumentCaptor<StorageFilesDeleteEvent> eventCaptor =
                ArgumentCaptor.forClass(StorageFilesDeleteEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().keys())
                .containsExactly("thumbnail-key", "movie-key", TRAILER_KEY);
        verify(storageKeyPolicy).validateMovieTrailerKey(1L, TRAILER_KEY);
        verify(storageService, never()).delete(org.mockito.ArgumentMatchers.anyString());
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
