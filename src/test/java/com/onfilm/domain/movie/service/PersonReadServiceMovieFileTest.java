package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.ForbiddenMovieAccessException;
import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieMediaServiceTest {
    private static final String TRAILER_KEY =
            "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000.mp4";
    @Mock MovieRepository movieRepository;
    @Mock MoviePersonRepository moviePersonRepository;
    @Mock CurrentPersonProvider currentPersonProvider;
    @Mock StorageService storageService;
    @Mock StorageKeyFactory storageKeyFactory;
    @Mock StorageKeyPolicy storageKeyPolicy;
    @Mock MediaEncodingService mediaEncodingService;
    @Mock StorageFileDeletionPublisher deletionPublisher;
    @InjectMocks MovieMediaService movieMediaService;

    @Test
    void deleteAllClearsEntityAndPublishesDeletionAfterCommitEvent() {
        Person person = mock(Person.class);
        given(person.getId()).willReturn(7L);
        given(currentPersonProvider.getRequired()).willReturn(person);
        given(moviePersonRepository.findByPersonIdAndMovieId(7L, 1L)).willReturn(mock(MoviePerson.class));
        Movie movie = Movie.create("Test", 120, 2020, "movie-key", "thumbnail-key", AgeRating.ALL);
        Trailer trailer = movie.addTrailer(TRAILER_KEY);
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));

        movieMediaService.deleteAll(1L);

        assertThat(movie.getMovieUrl()).isNull();
        assertThat(movie.getThumbnailUrl()).isNull();
        assertThat(movie.getTrailers()).isEmpty();
        assertThat(trailer.getMovie()).isNull();
        verify(storageKeyPolicy).validateMovieTrailerKey(1L, TRAILER_KEY);
        verify(deletionPublisher).publish(List.of("thumbnail-key", "movie-key", TRAILER_KEY));
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void editValidationRejectsMovieThatDoesNotBelongToCurrentPerson() {
        Person person = mock(Person.class);
        given(person.getId()).willReturn(7L);
        given(currentPersonProvider.getRequired()).willReturn(person);
        given(moviePersonRepository.findByPersonIdAndMovieId(7L, 1L)).willReturn(null);

        assertThatThrownBy(() -> movieMediaService.validateCanEdit(1L))
                .isInstanceOfSatisfying(ForbiddenMovieAccessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN_MOVIE_ACCESS));
        verifyNoInteractions(movieRepository);
    }
}
