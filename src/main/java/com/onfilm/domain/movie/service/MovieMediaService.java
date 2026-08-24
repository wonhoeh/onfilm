package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.ForbiddenMovieAccessException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MovieMediaService {

    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final CurrentPersonProvider currentPersonProvider;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;
    private final StorageKeyPolicy storageKeyPolicy;
    private final MediaEncodingService mediaEncodingService;
    private final StorageFileDeletionPublisher deletionPublisher;

    public void validateCanEdit(Long movieId) {
        editableMovie(movieId);
    }

    public UploadResultResponse replaceThumbnail(Long movieId, MultipartFile file) {
        Movie movie = editableMovie(movieId);
        String key = storageKeyFactory.movieThumbnail(movieId, ".jpg");
        return encodeAndApply(file, key, true, () -> movie.changeThumbnailUrl(key), movie.getThumbnailUrl());
    }

    public UploadResultResponse addTrailer(Long movieId, MultipartFile file) {
        Movie movie = editableMovie(movieId);
        String key = storageKeyFactory.movieTrailer(movieId, ".mp4");
        return encodeAndApply(file, key, false, () -> movie.addTrailer(key), null);
    }

    public UploadResultResponse replaceMovieFile(Long movieId, MultipartFile file) {
        Movie movie = editableMovie(movieId);
        String key = storageKeyFactory.movieFile(movieId, ".mp4");
        return encodeAndApply(file, key, false, () -> movie.changeMovieUrl(key), movie.getMovieUrl());
    }

    public void deleteThumbnail(Long movieId) {
        Movie movie = editableMovie(movieId);
        String oldKey = movie.getThumbnailUrl();
        movie.clearThumbnailUrl();
        deletionPublisher.publish(oldKey);
    }

    public void deleteTrailers(Long movieId) {
        Movie movie = editableMovie(movieId);
        List<String> keys = trailerKeys(movieId, movie);
        movie.clearTrailers();
        deletionPublisher.publish(keys);
    }

    public void deleteMovieFile(Long movieId) {
        Movie movie = editableMovie(movieId);
        String oldKey = movie.getMovieUrl();
        movie.clearMovieUrl();
        deletionPublisher.publish(oldKey);
    }

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

    private UploadResultResponse encodeAndApply(
            MultipartFile file,
            String key,
            boolean image,
            Runnable mutation,
            String oldKey
    ) {
        Path source = toTempFile(file);
        Path encoded = null;
        try {
            encoded = image
                    ? mediaEncodingService.encodeImage(source, 1280, 720)
                    : mediaEncodingService.encodeVideo(source, 720, 3000);
            storageService.save(key, encoded);
            try {
                mutation.run();
                if (oldKey != null && !oldKey.equals(key)) deletionPublisher.publish(oldKey);
                return new UploadResultResponse(key, storageService.toPublicUrl(key));
            } catch (RuntimeException exception) {
                try {
                    storageService.delete(key);
                } catch (RuntimeException ignored) {
                    // 새 파일 보상 삭제는 최선 노력으로 수행한다.
                }
                throw exception;
            }
        } finally {
            deleteTemp(source);
            deleteTemp(encoded);
        }
    }

    private static Path toTempFile(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("onfilm-upload-", ".tmp");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void deleteTemp(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일 정리는 최선 노력으로 수행한다.
        }
    }
}
