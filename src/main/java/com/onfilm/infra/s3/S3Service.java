package com.onfilm.infra.s3;

import com.onfilm.domain.global.error.exception.FileUploadException;
import com.onfilm.infra.s3.FileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    public String upload(MultipartFile multipartFile, FileType fileType) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        try {
            // 1. 파일 확장자 추출
            String originalFilename = multipartFile.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 2. UUID로 고유한 파일명 생성
            String uniqueFileName = UUID.randomUUID().toString() + extension;

            // 3. 폴더 + 파일명 조합
            String key = fileType.getFolder() + uniqueFileName;

            // 4. S3에 파일 업로드 (InputStream 방식, 메모리 절약)
            try (InputStream inputStream = multipartFile.getInputStream()) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(multipartFile.getContentType()) // 파일 MIME 타입 지정
                        .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, multipartFile.getSize()));
            }

            // 6. 업로드된 파일의 URL 반환
            return getFileUrl(key);
        } catch (Exception e) {
            throw new FileUploadException("파일 업로드 실패", e);
        }
    }

    public String getFileUrl(String fileName) {
        return s3Client.utilities()
                .getUrl(builder -> builder.bucket(bucketName).key(fileName))
                .toString();
    }
}
