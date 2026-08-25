package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(MovieMediaTransactionBoundaryTest.TestConfig.class)
class MovieMediaTransactionBoundaryTest {

    @Autowired
    private MovieMediaService service;

    @Autowired
    private CurrentPersonProvider currentPersonProvider;

    @Autowired
    private MoviePersonRepository moviePersonRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private StorageKeyFactory storageKeyFactory;

    @Autowired
    private MediaEncodingService mediaEncodingService;

    @Autowired
    private StorageFileDeletionPublisher deletionPublisher;

    @Test
    void encodesAndStoresOutsideTransactionThenMutatesMovieInsideTransaction() {
        String oldKey = "movie/1/old.mp4";
        String newKey = "movie/1/file/550e8400-e29b-41d4-a716-446655440000.mp4";
        MockMultipartFile file = new MockMultipartFile(
                "file", "movie.mp4", "video/mp4", new byte[]{1}
        );
        Person person = mock(Person.class);
        Movie movie = mock(Movie.class);
        given(person.getId()).willReturn(7L);
        given(movie.getMovieUrl()).willReturn(oldKey);
        given(currentPersonProvider.getRequired()).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return person;
        });
        given(moviePersonRepository.findByPersonIdAndMovieId(7L, 1L))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isTrue();
                    return mock(MoviePerson.class);
                });
        given(movieRepository.findById(1L)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(movie);
        });
        given(storageKeyFactory.movieFile(1L, ".mp4")).willReturn(newKey);
        given(mediaEncodingService.encodeVideo(any(Path.class), eq(720), eq(3000)))
                .willAnswer(invocation -> {
                    assertThat(
                            TransactionSynchronizationManager.isActualTransactionActive()
                    ).isFalse();
                    return invocation.getArgument(0);
                });
        given(storageService.save(
                eq(newKey),
                any(Path.class)
        )).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return newKey;
        });
        given(storageService.toPublicUrl(newKey)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "https://cdn.example/" + newKey;
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(deletionPublisher).publish(oldKey);

        service.replaceMovieFile(1L, file);

        verify(movie).changeMovieUrl(newKey);
        verify(deletionPublisher).publish(oldKey);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        MovieRepository movieRepository() {
            return mock(MovieRepository.class);
        }

        @Bean
        MoviePersonRepository moviePersonRepository() {
            return mock(MoviePersonRepository.class);
        }

        @Bean
        CurrentPersonProvider currentPersonProvider() {
            return mock(CurrentPersonProvider.class);
        }

        @Bean
        StorageService storageService() {
            return mock(StorageService.class);
        }

        @Bean
        StorageKeyFactory storageKeyFactory() {
            return mock(StorageKeyFactory.class);
        }

        @Bean
        StorageKeyPolicy storageKeyPolicy() {
            return mock(StorageKeyPolicy.class);
        }

        @Bean
        MediaEncodingService mediaEncodingService() {
            return mock(MediaEncodingService.class);
        }

        @Bean
        StorageFileDeletionPublisher deletionPublisher() {
            return mock(StorageFileDeletionPublisher.class);
        }

        @Bean
        MovieMediaTransactionService transactionService(
                MovieRepository movieRepository,
                MoviePersonRepository moviePersonRepository,
                CurrentPersonProvider currentPersonProvider,
                StorageKeyPolicy storageKeyPolicy,
                StorageFileDeletionPublisher deletionPublisher
        ) {
            return new MovieMediaTransactionService(
                    movieRepository,
                    moviePersonRepository,
                    currentPersonProvider,
                    storageKeyPolicy,
                    deletionPublisher
            );
        }

        @Bean
        MovieMediaService movieMediaService(
                MovieMediaTransactionService transactionService,
                StorageService storageService,
                StorageKeyFactory storageKeyFactory,
                MediaEncodingService mediaEncodingService
        ) {
            return new MovieMediaService(
                    transactionService,
                    storageService,
                    storageKeyFactory,
                    mediaEncodingService
            );
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:movie-media-boundary-test;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
