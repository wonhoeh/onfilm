package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.PersonReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/movie")
public class MovieFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final PersonReadService personReadService;
    private final MediaEncodingService mediaEncodingService;

    @PostMapping("/{movieId}/thumbnail")
    public UploadResultResponse uploadThumbnail(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieThumbnail(movieId, ".jpg");
        Path source = toTempFile(file);
        Path encoded = null;
        try {
            encoded = mediaEncodingService.encodeImage(source, 1280, 720);
            storage.save(key, encoded);
            try {
                personReadService.updateMovieThumbnail(movieId, key);
            } catch (RuntimeException e) {
                storage.delete(key);
                throw e;
            }
            return new UploadResultResponse(key, storage.toPublicUrl(key));
        } finally {
            deleteTemp(source);
            deleteTemp(encoded);
        }
    }

    @DeleteMapping("/{movieId}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(@PathVariable Long movieId) {
        personReadService.deleteMovieThumbnail(movieId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{movieId}/trailer")
    public UploadResultResponse uploadTrailer(@PathVariable Long movieId,
                                              @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieTrailer(movieId, ".mp4");
        Path source = toTempFile(file);
        Path encoded = null;
        try {
            encoded = mediaEncodingService.encodeVideo(source, 720, 3000);
            storage.save(key, encoded);
            try {
                personReadService.addMovieTrailer(movieId, key);
            } catch (RuntimeException e) {
                storage.delete(key);
                throw e;
            }
            return new UploadResultResponse(key, storage.toPublicUrl(key));
        } finally {
            deleteTemp(source);
            deleteTemp(encoded);
        }
    }

    @DeleteMapping("/{movieId}/trailer")
    public ResponseEntity<Void> deleteTrailer(@PathVariable Long movieId) {
        personReadService.deleteMovieTrailers(movieId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{movieId}/file")
    public UploadResultResponse uploadMovieFile(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieFile(movieId, ".mp4");
        Path source = toTempFile(file);
        Path encoded = null;
        try {
            encoded = mediaEncodingService.encodeVideo(source, 720, 3000);
            storage.save(key, encoded);
            try {
                personReadService.updateMovieFile(movieId, key);
            } catch (RuntimeException e) {
                storage.delete(key);
                throw e;
            }
            return new UploadResultResponse(key, storage.toPublicUrl(key));
        } finally {
            deleteTemp(source);
            deleteTemp(encoded);
        }
    }

    @DeleteMapping("/{movieId}/file")
    public ResponseEntity<Void> deleteMovieFile(@PathVariable Long movieId) {
        personReadService.deleteMovieFile(movieId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovieFiles(@PathVariable Long movieId) {
        personReadService.deleteMovieFiles(movieId);
        return ResponseEntity.noContent().build();
    }

    private Path toTempFile(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("onfilm-upload-", ".tmp");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteTemp(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
