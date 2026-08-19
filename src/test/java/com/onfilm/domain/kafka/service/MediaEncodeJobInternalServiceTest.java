package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.dto.TrailerMediaUpdateRequest;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MediaEncodeJobInternalServiceTest {

    @Mock
    private MediaEncodeJobRepository mediaEncodeJobRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private StorageKeyPolicy storageKeyPolicy;

    @InjectMocks
    private MediaEncodeJobInternalService service;

    @Test
    void updateTrailerMedia_isIdempotentForRepeatedCallback() {
        MediaEncodeJob job = mock(MediaEncodeJob.class);
        Movie movie = Movie.create(
                "Test Movie",
                120,
                2020,
                "movie-key",
                null,
                AgeRating.ALL
        );
        given(job.getJobType()).willReturn(EncodeJobType.TRAILER);
        given(job.getMovieId()).willReturn(1L);
        given(mediaEncodeJobRepository.findById("job-id"))
                .willReturn(Optional.of(job));
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));
        String trailerKey =
                "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8";
        TrailerMediaUpdateRequest request = new TrailerMediaUpdateRequest(trailerKey);

        service.updateTrailerMedia("job-id", request);
        service.updateTrailerMedia("job-id", request);

        assertThat(movie.getTrailers())
                .extracting(trailer -> trailer.getStorageKey())
                .containsExactly(trailerKey);
        verify(storageKeyPolicy, org.mockito.Mockito.times(2))
                .validateMovieTrailerKey(1L, trailerKey);
    }
}
