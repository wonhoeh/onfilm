package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.EmptyFileException;
import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieMediaService {

    private final MovieMediaTransactionService transactionService;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;
    private final MediaEncodingService mediaEncodingService;

    public void validateCanEdit(Long movieId) {
        transactionService.validateCanEdit(movieId);
    }

    public UploadResultResponse replaceThumbnail(Long movieId, MultipartFile file) {
        transactionService.validateCanEdit(movieId);
        String key = storageKeyFactory.movieThumbnail(movieId, ".jpg");
        return encodeAndApply(
                file,
                key,
                true,
                () -> transactionService.replaceThumbnail(movieId, key)
        );
    }

    public UploadResultResponse addTrailer(Long movieId, MultipartFile file) {
        transactionService.validateCanEdit(movieId);
        String key = storageKeyFactory.movieTrailer(movieId, ".mp4");
        return encodeAndApply(
                file,
                key,
                false,
                () -> transactionService.addTrailer(movieId, key)
        );
    }

    public UploadResultResponse replaceMovieFile(Long movieId, MultipartFile file) {
        transactionService.validateCanEdit(movieId);
        String key = storageKeyFactory.movieFile(movieId, ".mp4");
        return encodeAndApply(
                file,
                key,
                false,
                () -> transactionService.replaceMovieFile(movieId, key)
        );
    }

    public void deleteThumbnail(Long movieId) {
        transactionService.deleteThumbnail(movieId);
    }

    public void deleteTrailers(Long movieId) {
        transactionService.deleteTrailers(movieId);
    }

    public void deleteMovieFile(Long movieId) {
        transactionService.deleteMovieFile(movieId);
    }

    public void deleteAll(Long movieId) {
        transactionService.deleteAll(movieId);
    }

    private UploadResultResponse encodeAndApply(
            MultipartFile file,
            String key,
            boolean image,
            Runnable mutation
    ) {
        Path source = toTempFile(file);
        Path encoded = null;
        try {
            encoded = image
                    ? mediaEncodingService.encodeImage(source, 1280, 720)
                    : mediaEncodingService.encodeVideo(source, 720, 3000);
            storageService.save(key, encoded);
            try {
                UploadResultResponse response =
                        new UploadResultResponse(key, storageService.toPublicUrl(key));
                mutation.run();
                return response;
            } catch (RuntimeException exception) {
                compensateNewFile(key);
                throw exception;
            }
        } finally {
            deleteTemp(source);
            deleteTemp(encoded);
        }
    }

    private void compensateNewFile(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException exception) {
            log.error("Failed to compensate newly stored movie media. key={}", key, exception);
        }
    }

    private static Path toTempFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
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
