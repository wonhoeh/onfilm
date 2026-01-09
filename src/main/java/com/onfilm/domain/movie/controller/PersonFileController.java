package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.FileCommandService;
import com.onfilm.domain.movie.service.PersonReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/person")
@Slf4j
public class PersonFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final FileCommandService commandService;
    private final PersonReadService personReadService;

    @PostMapping("/me/profile")
    public UploadResultResponse uploadMyProfile(@RequestParam("file") MultipartFile file) {
        Long personId = personReadService.currentPersonId();

        // 기존 프로필 이미지 있는 경우, 삭제할 때 사용
        String oldKey = personReadService.findProfileImageKey(personId);
        log.info("oldKey = {}", oldKey);

        // oldKey: http://localhost:8080/files/profile/1/avatar/56624b7e-c98d-4174-8983-fe3c21c6f1dc.jpeg
        // 56624b7e-c98d-4174-8983-fe3c21c6f1dc.jpeg

        // 새로운 프로필 이미지 key 생성 및 저장
        String newKey = keyFactory.profileAvatar(personId, extOf(file));
        storage.save(newKey, file);

        try {
            commandService.updatePersonProfileImage(personId, newKey);       // DB 반영

            // 이전 프로필 이미지 삭제
            if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
                storage.delete(oldKey);
            }

            return new UploadResultResponse(newKey, storage.toPublicUrl(newKey));
        } catch (Exception e) {
            // DB 반영 실패 시: 새로 올린 파일을 롤백 삭제(선택)
            // 이걸 넣으면 스토리지 orphan을 줄일 수 있음
            try { storage.delete(newKey); } catch (Exception ignore) {}
            throw e;
        }
    }

    @PostMapping("/me/gallery")
    public UploadResultResponse uploadMyGallery(@RequestParam("file") MultipartFile file) {
        Long personId = personReadService.currentPersonId();

        String key = keyFactory.gallery(personId, extOf(file));
        storage.save(key, file);
        commandService.addPersonGalleryImage(personId, key); // 아래 3)에서 추가

        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    // 확장자 추출 예) .jpeg / .mp4
    private String extOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return (idx < 0) ? "" : name.substring(idx).toLowerCase();
    }
}
