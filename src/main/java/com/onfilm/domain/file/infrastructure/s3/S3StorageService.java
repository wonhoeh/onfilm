package com.onfilm.domain.file.infrastructure.s3;

import com.onfilm.domain.file.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;
    private final String region;

    public S3StorageService(
            S3Client s3Client,
            @Value("${file.storage.bucket}") String bucket,
            @Value("${file.public-base-url:}") String publicBaseUrl,
            @Value("${file.storage.region:}") String region,
            @Value("${file.storage.region-static:}") String regionStatic
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = (publicBaseUrl == null) ? "" : publicBaseUrl;
        this.region = (region != null && !region.isBlank()) ? region : regionStatic;
    }

    @Override
    public String save(String key, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("EMPTY_FILE");

        String normalized = normalizeKey(key);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(normalized)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
            return normalized;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String save(String key, Path source) {
        if (source == null) throw new IllegalArgumentException("EMPTY_FILE");

        String normalized = normalizeKey(key);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(normalized)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(source));
        return normalized;
    }

    @Override
    public void delete(String key) {
        String normalized = normalizeKey(key);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(normalized)
                .build();
        s3Client.deleteObject(request);
    }

    @Override
    public String toPublicUrl(String key) {
        if (key == null || key.isBlank()) return null;

        String trimmed = key.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        String normalized = normalizeKey(trimmed);
        String base = publicBaseUrl;
        if (base == null || base.isBlank()) {
            if (region == null || region.isBlank()) {
                throw new IllegalStateException("file.storage.region is required to build S3 URL");
            }
            base = "https://" + bucket + ".s3." + region + ".amazonaws.com";
        } else {
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        }
        return base + "/" + normalized;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("EMPTY_KEY");
        String normalized = key.replace("\\", "/");
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }
}
