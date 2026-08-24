package com.onfilm.domain.movie.controller;

import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.PresignUploadRequest;
import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;
import com.onfilm.domain.kafka.service.MediaEncodeJobCommandService;
import com.onfilm.domain.kafka.service.MediaUploadRequestService;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.movie.dto.MediaEncodeJobResponse;
import com.onfilm.domain.movie.dto.MediaUploadCompleteRequest;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.MovieMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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
    private final MovieMediaService movieMediaService;
    private final MediaEncodeJobCommandService mediaEncodeJobCommandService;
    private final MediaUploadRequestService mediaUploadRequestService;

    @Value("${file.storage.bucket:}")
    private String storageBucket;

    @PostMapping("/{movieId}/thumbnail")
    public UploadResultResponse uploadThumbnail(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        return movieMediaService.replaceThumbnail(movieId, file);
    }

    @DeleteMapping("/{movieId}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(@PathVariable Long movieId) {
        movieMediaService.deleteThumbnail(movieId);
        return ResponseEntity.noContent().build();
    }

    // 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL 을 발급한다.
    @PostMapping("/{movieId}/thumbnail/presign")
    public ResponseEntity<PresignedUploadUrlResponse> presignThumbnailUpload(@PathVariable Long movieId,
                                                                             @Valid @RequestBody PresignUploadRequest request) {
        validateMovieUploadPermission(movieId);
        String requestId = mediaUploadRequestService.newRequestId();
        String sourceKey = rawSourceKey(movieId, "thumbnail", requestId, extensionForImage(request.contentType()));
        return ResponseEntity.ok(mediaUploadRequestService.issue(
                SecurityUtil.currentUserId(), movieId, EncodeJobType.THUMBNAIL, sourceKey, request.contentType()));
    }

    // S3 업로드 완료 후 인코딩 작업만 Kafka에 위임한다.
    @PostMapping("/{movieId}/thumbnail/complete")
    public ResponseEntity<MediaEncodeJobResponse> completeThumbnailUpload(@PathVariable Long movieId,
                                                                          @Valid @RequestBody MediaUploadCompleteRequest request) {
        String targetKey = keyFactory.movieThumbnail(movieId, ".jpg");
        String jobId = enqueueThumbnailJob(movieId, request, targetKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MediaEncodeJobResponse(jobId, request.sourceKey(), targetKey));
    }

    @PostMapping("/{movieId}/trailer")
    public UploadResultResponse uploadTrailer(@PathVariable Long movieId,
                                              @RequestParam("file") MultipartFile file) {
        return movieMediaService.addTrailer(movieId, file);
    }

    @DeleteMapping("/{movieId}/trailer")
    public ResponseEntity<Void> deleteTrailer(@PathVariable Long movieId) {
        movieMediaService.deleteTrailers(movieId);
        return ResponseEntity.noContent().build();
    }

    // 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL 을 발급한다.
    @PostMapping("/{movieId}/trailer/presign")
    public ResponseEntity<PresignedUploadUrlResponse> presignTrailerUpload(@PathVariable Long movieId,
                                                                           @Valid @RequestBody PresignUploadRequest request) {
        validateMovieUploadPermission(movieId);
        String requestId = mediaUploadRequestService.newRequestId();
        String sourceKey = rawSourceKey(movieId, "trailer", requestId, extensionForVideo(request.contentType()));
        return ResponseEntity.ok(mediaUploadRequestService.issue(
                SecurityUtil.currentUserId(), movieId, EncodeJobType.TRAILER, sourceKey, request.contentType()));
    }

    // S3 업로드 완료 후 인코딩 작업만 Kafka에 위임한다.
    @PostMapping("/{movieId}/trailer/complete")
    public ResponseEntity<MediaEncodeJobResponse> completeTrailerUpload(@PathVariable Long movieId,
                                                                        @Valid @RequestBody MediaUploadCompleteRequest request) {
        String targetKey = keyFactory.movieTrailerHlsTarget(movieId);
        String jobId = enqueueTrailerJob(movieId, request, targetKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MediaEncodeJobResponse(jobId, request.sourceKey(), targetKey));
    }

    @PostMapping("/{movieId}/file")
    public UploadResultResponse uploadMovieFile(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        return movieMediaService.replaceMovieFile(movieId, file);
    }

    @DeleteMapping("/{movieId}/file")
    public ResponseEntity<Void> deleteMovieFile(@PathVariable Long movieId) {
        movieMediaService.deleteMovieFile(movieId);
        return ResponseEntity.noContent().build();
    }

    // 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL 을 발급한다.
    @PostMapping("/{movieId}/file/presign")
    public ResponseEntity<PresignedUploadUrlResponse> presignMovieFileUpload(@PathVariable Long movieId,
                                                                             @Valid @RequestBody PresignUploadRequest request) {
        validateMovieUploadPermission(movieId);
        String requestId = mediaUploadRequestService.newRequestId();
        String sourceKey = rawSourceKey(movieId, "file", requestId, extensionForVideo(request.contentType()));
        return ResponseEntity.ok(mediaUploadRequestService.issue(
                SecurityUtil.currentUserId(), movieId, EncodeJobType.MOVIE, sourceKey, request.contentType()));
    }

    // S3 업로드 완료 후 인코딩 작업만 Kafka에 위임한다.
    @PostMapping("/{movieId}/file/complete")
    public ResponseEntity<MediaEncodeJobResponse> completeMovieFileUpload(@PathVariable Long movieId,
                                                                          @Valid @RequestBody MediaUploadCompleteRequest request) {
        String targetKey = keyFactory.movieFileHlsTarget(movieId);
        String jobId = enqueueMovieJob(movieId, request, targetKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MediaEncodeJobResponse(jobId, request.sourceKey(), targetKey));
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovieFiles(@PathVariable Long movieId) {
        movieMediaService.deleteAll(movieId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/raw-upload", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> uploadRawMovieAsset(@RequestParam("sourceKey") String sourceKey,
                                                    HttpServletRequest request) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!sourceKey.startsWith("movie/") || !sourceKey.contains("/raw/")) {
            return ResponseEntity.badRequest().build();
        }
        mediaUploadRequestService.authorizeRawUpload(SecurityUtil.currentUserId(), sourceKey);

        Path temp = null;
        try {
            temp = Files.createTempFile("onfilm-raw-upload-", ".bin");
            try (InputStream in = request.getInputStream()) {
                long copied = Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                if (copied <= 0) {
                    return ResponseEntity.badRequest().build();
                }
            }
            storage.save(sourceKey, temp);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteTemp(temp);
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
        return mediaEncodeJobCommandService.requestThumbnailEncoding(
                request.requestId(),
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
        return mediaEncodeJobCommandService.requestTrailerEncoding(
                request.requestId(),
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
        return mediaEncodeJobCommandService.requestMovieEncoding(
                request.requestId(),
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
        movieMediaService.validateCanEdit(movieId);
    }

    // 원본 파일은 raw 경로에 먼저 저장하고, 인코딩 결과는 별도 targetKey 로 분리한다.
    private String rawSourceKey(Long movieId, String mediaType, String requestId, String extension) {
        return "movie/" + movieId + "/raw/" + mediaType + "/" + requestId + extension;
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
            default -> throw new IllegalArgumentException("unsupported video contentType");
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
            default -> throw new IllegalArgumentException("unsupported image contentType");
        };
    }
}
