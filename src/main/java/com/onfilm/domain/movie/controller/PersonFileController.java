package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.PersonReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/person")
@Slf4j
public class PersonFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final PersonReadService personReadService;

    @PostMapping("/me/profile")
    public UploadResultResponse uploadMyProfile(@RequestParam("file") MultipartFile file) {
        Long personId = personReadService.findCurrentPersonId();

        // 기존 프로필 이미지 있는 경우, 삭제할 때 사용
        String oldKey = personReadService.findProfileImageKey(personId);

        String oldDeleteKey = toStorageKey(oldKey);

        // 새로운 프로필 이미지 key 생성 및 저장
        String newKey = keyFactory.profileAvatar(personId, extOf(file));
        storage.save(newKey, file);

        try {
            personReadService.updatePersonProfileImage(personId, newKey);       // DB 반영

            // 이전 프로필 이미지 삭제
            if (oldDeleteKey != null && !oldDeleteKey.isBlank() && !oldDeleteKey.equals(newKey)) {
                storage.delete(oldDeleteKey);
            }

            return new UploadResultResponse(newKey, storage.toPublicUrl(newKey));
        } catch (Exception e) {
            // DB 반영 실패 시: 새로 올린 파일을 롤백 삭제(선택)
            // 이걸 넣으면 스토리지 orphan을 줄일 수 있음
            try { storage.delete(newKey); } catch (Exception ignore) {}
            throw e;
        }
    }

    @DeleteMapping("/me/profile")
    public ResponseEntity<Void> deleteMyProfile() {
        Long personId = personReadService.findCurrentPersonId();
        String oldKey = personReadService.findProfileImageKey(personId);
        String oldDeleteKey = toStorageKey(oldKey);

        personReadService.clearProfileImage(personId);

        if (oldDeleteKey != null && !oldDeleteKey.isBlank()) {
            storage.delete(oldDeleteKey);
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/gallery")
    public UploadResultResponse uploadMyGallery(@RequestParam("file") MultipartFile file) {
        Long personId = personReadService.findCurrentPersonId();

        String key = keyFactory.gallery(personId, extOf(file));
        storage.save(key, file);
        personReadService.addPersonGalleryImage(personId, key); // 아래 3)에서 추가

        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    // 확장자 추출 예) .jpeg / .mp4
    private String extOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return (idx < 0) ? "" : name.substring(idx).toLowerCase();
    }

    // "profile/1/avatar/uuid.jpeg" 형식으로 변환
    private String toStorageKey(String value) {
        if (value == null || value.isBlank()) return null;
        String s = value.trim();

        // URL이면 path만 뽑기
        if (s.startsWith("http://") || s.startsWith("https://")) {
            s = URI.create(s).getPath(); // 예: "/files/profile/1/avatar/uuid.jpeg"
        }

        // "/files/" 제거
        if (s.startsWith("/files/")) s = s.substring("/files/".length());

        // 앞 "/" 제거 (남아있으면)
        if (s.startsWith("/")) s = s.substring(1);

        return s; // 예: "profile/1/avatar/uuid.jpeg"
    }
}
