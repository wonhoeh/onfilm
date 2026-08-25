package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.ForbiddenMovieAccessException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieMediaTransactionService {

    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final CurrentPersonProvider currentPersonProvider;
    private final StorageKeyPolicy storageKeyPolicy;
    private final StorageFileDeletionPublisher deletionPublisher;

    @Transactional(readOnly = true)
    public void validateCanEdit(Long movieId) {
        editableMovie(movieId);
    }

    @Transactional
    public void replaceThumbnail(Long movieId, String newKey) {
        Movie movie = editableMovie(movieId);
        String oldKey = movie.getThumbnailUrl();
        movie.changeThumbnailUrl(newKey);
        publishReplacedKey(oldKey, newKey);
    }

    @Transactional
    public void addTrailer(Long movieId, String key) {
        editableMovie(movieId).addTrailer(key);
    }

    @Transactional
    public void replaceMovieFile(Long movieId, String newKey) {
        Movie movie = editableMovie(movieId);
        String oldKey = movie.getMovieUrl();
        movie.changeMovieUrl(newKey);
        publishReplacedKey(oldKey, newKey);
    }

    @Transactional
    public void deleteThumbnail(Long movieId) {
        Movie movie = editableMovie(movieId);
        String oldKey = movie.getThumbnailUrl();
        movie.clearThumbnailUrl();
        deletionPublisher.publish(oldKey);
    }

    @Transactional
    public void deleteTrailers(Long movieId) {
        Movie movie = editableMovie(movieId);
        List<String> keys = trailerKeys(movieId, movie);
        movie.clearTrailers();
        deletionPublisher.publish(keys);
    }

    @Transactional
    public void deleteMovieFile(Long movieId) {
        Movie movie = editableMovie(movieId);
        String oldKey = movie.getMovieUrl();
        movie.clearMovieUrl();
        deletionPublisher.publish(oldKey);
    }

    @Transactional
    public void deleteAll(Long movieId) {
        Movie movie = editableMovie(movieId);
        List<String> keys = new ArrayList<>();
        keys.add(movie.getThumbnailUrl());
        keys.add(movie.getMovieUrl());
        keys.addAll(trailerKeys(movieId, movie));
        movie.clearThumbnailUrl();
        movie.clearMovieUrl();
        movie.clearTrailers();
        deletionPublisher.publish(keys);
    }

    private Movie editableMovie(Long movieId) {
        Person person = currentPersonProvider.getRequired();
        if (moviePersonRepository.findByPersonIdAndMovieId(person.getId(), movieId) == null) {
            throw new ForbiddenMovieAccessException();
        }
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
    }

    private List<String> trailerKeys(Long movieId, Movie movie) {
        return movie.getTrailers().stream()
                .map(Trailer::getStorageKey)
                .peek(key -> storageKeyPolicy.validateMovieTrailerKey(movieId, key))
                .toList();
    }

    private void publishReplacedKey(String oldKey, String newKey) {
        if (oldKey != null && !oldKey.equals(newKey)) {
            deletionPublisher.publish(oldKey);
        }
    }
}
