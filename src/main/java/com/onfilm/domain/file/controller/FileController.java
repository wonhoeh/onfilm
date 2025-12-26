package com.onfilm.domain.file.controller;

import com.onfilm.domain.file.dto.FileUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileController {

    private final Path uploadDir;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    public FileController(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            // 개발단계라면 여기 허용 타입을 더 늘려도 됨
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        long max = 10 * 1024 * 1024;
        if (file.getSize() > max) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        Files.createDirectories(uploadDir);

        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String safeName = UUID.randomUUID() + "_" + original.replaceAll("[\\s]+", "_");
        Path target = uploadDir.resolve(safeName);

        // 덮어쓰기 방지
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        // ✅ 정적 서빙 URL로 반환
        String url = "/uploads/" + safeName;
        return ResponseEntity.status(HttpStatus.CREATED).body(new FileUploadResponse(url));
    }
}