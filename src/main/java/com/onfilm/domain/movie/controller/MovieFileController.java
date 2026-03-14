package com.onfilm.domain.movie.controller;

import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.service.MediaEncodingService;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.PresignUploadRequest;
import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;
import com.onfilm.domain.kafka.service.MediaEncodeJobCommandService;
import com.onfilm.domain.kafka.service.MediaPresignedUploadService;
import com.onfilm.domain.movie.dto.MediaEncodeJobResponse;
import com.onfilm.domain.movie.dto.MediaUploadCompleteRequest;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.PersonReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/movie")
public class MovieFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final PersonReadService personReadService;
    private final MediaEncodingService mediaEncodingService;
    private final ObjectProvider<MediaEncodeJobCommandService> mediaEncodeJobCommandServiceProvider;
    private final ObjectProvider<MediaPresignedUploadService> mediaPresignedUploadServiceProvider;

    @Value("${file.storage.bucket:}")
    private String storageBucket;

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

    // 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL 을 발급한다.
    @PostMapping("/{movieId}/thumbnail/presign")
    public ResponseEntity<PresignedUploadUrlResponse> presignThumbnailUpload(@PathVariable Long movieId,
                                                                             @RequestBody PresignUploadRequest request) {
        validateMovieUploadPermission(movieId);
        String sourceKey = rawSourceKey(movieId, "thumbnail", extensionForImage(request.contentType()));
        return ResponseEntity.ok(requiredPresignedUploadService().createUploadUrl(sourceKey, request.contentType()));
    }

    // S3 업로드 완료 후 인코딩 작업만 Kafka에 위임한다.
    @PostMapping("/{movieId}/thumbnail/complete")
    public ResponseEntity<MediaEncodeJobResponse> completeThumbnailUpload(@PathVariable Long movieId,
                                                                          @RequestBody MediaUploadCompleteRequest request) {
        String targetKey = keyFactory.movieThumbnail(movieId, ".jpg");
        String jobId = enqueueThumbnailJob(movieId, request, targetKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MediaEncodeJobResponse(jobId, request.sourceKey(), targetKey));
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

    // 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL 을 발급한다.
    @PostMapping("/{movieId}/trailer/presign")
    public ResponseEntity<PresignedUploadUrlResponse> presignTrailerUpload(@PathVariable Long movieId,
                                                                           @RequestBody PresignUploadRequest request) {
        validateMovieUploadPermission(movieId);
        String sourceKey = rawSourceKey(movieId, "trailer", extensionForVideo(request.contentType()));
        return ResponseEntity.ok(requiredPresignedUploadService().createUploadUrl(sourceKey, request.contentType()));
    }

    // S3 업로드 완료 후 인코딩 작업만 Kafka에 위임한다.
    @PostMapping("/{movieId}/trailer/complete")
    public ResponseEntity<MediaEncodeJobResponse> completeTrailerUpload(@PathVariable Long movieId,
                                                                        @RequestBody MediaUploadCompleteRequest request) {
        String targetKey = keyFactory.movieTrailer(movieId, ".mp4");
        String jobId = enqueueTrailerJob(movieId, request, targetKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MediaEncodeJobResponse(jobId, request.sourceKey(), targetKey));
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

    // 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL 을 발급한다.
    @PostMapping("/{movieId}/file/presign")
    public ResponseEntity<PresignedUploadUrlResponse> presignMovieFileUpload(@PathVariable Long movieId,
                                                                             @RequestBody PresignUploadRequest request) {
        validateMovieUploadPermission(movieId);
        String sourceKey = rawSourceKey(movieId, "file", extensionForVideo(request.contentType()));
        return ResponseEntity.ok(requiredPresignedUploadService().createUploadUrl(sourceKey, request.contentType()));
    }

    // S3 업로드 완료 후 인코딩 작업만 Kafka에 위임한다.
    @PostMapping("/{movieId}/file/complete")
    public ResponseEntity<MediaEncodeJobResponse> completeMovieFileUpload(@PathVariable Long movieId,
                                                                          @RequestBody MediaUploadCompleteRequest request) {
        String targetKey = keyFactory.movieFile(movieId, ".mp4");
        String jobId = enqueueMovieJob(movieId, request, targetKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MediaEncodeJobResponse(jobId, request.sourceKey(), targetKey));
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

    private String enqueueThumbnailJob(Long movieId, MediaUploadCompleteRequest request, String targetKey) {
        validateMovieUploadRequest(movieId, request);
        return requiredJobCommandService().requestThumbnailEncoding(
                movieId,
                SecurityUtil.currentUserId(),
                storageBucket,
                request.sourceKey(),
                storageBucket,
                targetKey,
                request.contentType()
        );
    }

    private String enqueueTrailerJob(Long movieId, MediaUploadCompleteRequest request, String targetKey) {
        validateMovieUploadRequest(movieId, request);
        return requiredJobCommandService().requestTrailerEncoding(
                movieId,
                SecurityUtil.currentUserId(),
                storageBucket,
                request.sourceKey(),
                storageBucket,
                targetKey,
                request.contentType()
        );
    }

    private String enqueueMovieJob(Long movieId, MediaUploadCompleteRequest request, String targetKey) {
        validateMovieUploadRequest(movieId, request);
        return requiredJobCommandService().requestMovieEncoding(
                movieId,
                SecurityUtil.currentUserId(),
                storageBucket,
                request.sourceKey(),
                storageBucket,
                targetKey,
                request.contentType()
        );
    }

    private void validateMovieUploadRequest(Long movieId, MediaUploadCompleteRequest request) {
        validateMovieUploadPermission(movieId);
        if (request == null || request.sourceKey() == null || request.sourceKey().isBlank()) {
            throw new IllegalArgumentException("sourceKey is required");
        }
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new IllegalStateException("file.storage.bucket is required");
        }
    }

    private void validateMovieUploadPermission(Long movieId) {
        Long personId = personReadService.findCurrentPersonId();
        if (!personReadService.canEditMovie(personId, movieId)) {
            throw new IllegalStateException("FORBIDDEN_MOVIE_ACCESS");
        }
    }

    private MediaEncodeJobCommandService requiredJobCommandService() {
        MediaEncodeJobCommandService service = mediaEncodeJobCommandServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("MEDIA_ENCODE_PRODUCER_NOT_CONFIGURED");
        }
        return service;
    }

    private MediaPresignedUploadService requiredPresignedUploadService() {
        MediaPresignedUploadService service = mediaPresignedUploadServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("PRESIGNED_UPLOAD_NOT_CONFIGURED");
        }
        return service;
    }

    // 원본 파일은 raw 경로에 먼저 저장하고, 인코딩 결과는 별도 targetKey 로 분리한다.
    private String rawSourceKey(Long movieId, String mediaType, String extension) {
        return "movie/" + movieId + "/raw/" + mediaType + "/" + UUID.randomUUID() + extension;
    }

    private String extensionForVideo(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        return switch (contentType) {
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "video/x-msvideo" -> ".avi";
            case "video/x-matroska" -> ".mkv";
            case "video/webm" -> ".webm";
            default -> ".bin";
        };
    }

    private String extensionForImage(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
