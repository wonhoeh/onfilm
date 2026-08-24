package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.PersonMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/person")
public class PersonFileController {
    private final PersonMediaService personMediaService;

    @PostMapping("/me/profile")
    public UploadResultResponse uploadMyProfile(@RequestParam("file") MultipartFile file) {
        return personMediaService.replaceProfileImage(file);
    }

    @DeleteMapping("/me/profile")
    public ResponseEntity<Void> deleteMyProfile() {
        personMediaService.deleteProfileImage();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/gallery")
    public UploadResultResponse uploadMyGallery(@RequestParam("file") MultipartFile file) {
        return personMediaService.addGalleryImage(file);
    }

    @PostMapping("/me/storyboard")
    public UploadResultResponse uploadStoryboardImage(@RequestParam("file") MultipartFile file) {
        return personMediaService.uploadStoryboardImage(file);
    }
}
