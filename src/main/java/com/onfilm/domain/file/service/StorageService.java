package com.onfilm.domain.file.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * @param key  예: gallery/1/uuid.jpg
     * @return 저장된 key 그대로 반환하거나, 필요하면 정규화된 key 반환
     */
    String save(String key, MultipartFile file);

    void delete(String key);

    /**
     * key -> 접근 가능한 URL로 변환
     * local: http://localhost:8080/files/{key}
     * s3/cdn: https://cdn.../{key}
     */
    String toPublicUrl(String key);
}
