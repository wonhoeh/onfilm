package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.FileCommandService;
import com.onfilm.domain.movie.service.PersonReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/person")
public class PersonFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final FileCommandService commandService;
    private final PersonReadService personReadService;

    @PostMapping("/me/profile")
    public UploadResultResponse uploadMyProfile(@RequestParam("file") MultipartFile file) {
        Long personId = personReadService.currentPersonId();  // ✅ 내 personId

        String key = keyFactory.profileAvatar(personId, extOf(file)); // 키 생성
        storage.save(key, file);                                      // 저장
        commandService.updatePersonProfileImage(personId, key);       // DB 반영

        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    @PostMapping("/me/gallery")
    public UploadResultResponse uploadMyGallery(@RequestParam("file") MultipartFile file) {
        Long personId = personReadService.currentPersonId();

        String key = keyFactory.gallery(personId, extOf(file));
        storage.save(key, file);
        commandService.addPersonGalleryImage(personId, key); // 아래 3)에서 추가

        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    private String extOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return (idx < 0) ? "" : name.substring(idx).toLowerCase();
    }
}
