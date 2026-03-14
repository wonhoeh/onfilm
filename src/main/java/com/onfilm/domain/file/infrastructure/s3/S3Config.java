package com.onfilm.domain.file.infrastructure.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3Config {

    @Bean
    S3Client s3Client(
            @Value("${file.storage.region:}") String region,
            @Value("${file.storage.region-static:}") String regionStatic,
            @Value("${file.storage.access-key:}") String accessKey,
            @Value("${file.storage.secret-key:}") String secretKey
    ) {
        String resolvedRegion = (region != null && !region.isBlank()) ? region : regionStatic;
        if (resolvedRegion == null || resolvedRegion.isBlank()) {
            throw new IllegalStateException("file.storage.region is required for S3");
        }

        AwsCredentialsProvider credentialsProvider;
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            );
        } else {
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        return S3Client.builder()
                .region(Region.of(resolvedRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    S3Presigner s3Presigner(
            @Value("${file.storage.region:}") String region,
            @Value("${file.storage.region-static:}") String regionStatic,
            @Value("${file.storage.access-key:}") String accessKey,
            @Value("${file.storage.secret-key:}") String secretKey
    ) {
        String resolvedRegion = (region != null && !region.isBlank()) ? region : regionStatic;
        if (resolvedRegion == null || resolvedRegion.isBlank()) {
            throw new IllegalStateException("file.storage.region is required for S3");
        }

        AwsCredentialsProvider credentialsProvider;
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            );
        } else {
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        return S3Presigner.builder()
                .region(Region.of(resolvedRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
