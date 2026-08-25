package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonMediaService {

    private final PersonMediaTransactionService transactionService;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;

    public UploadResultResponse replaceProfileImage(MultipartFile file) {
        Long personId = transactionService.getCurrentPersonId();
        String newKey = storageKeyFactory.profileAvatar(personId, extensionOf(file));
        return saveAndApply(
                file,
                newKey,
                () -> transactionService.replaceProfileImage(newKey)
        );
    }

    public void deleteProfileImage() {
        transactionService.deleteProfileImage();
    }

    public UploadResultResponse addGalleryImage(MultipartFile file) {
        Long personId = transactionService.getCurrentPersonId();
        String key = storageKeyFactory.gallery(personId, extensionOf(file));
        return saveAndApply(file, key, () -> transactionService.addGalleryImage(key));
    }

    public UploadResultResponse uploadStoryboardImage(MultipartFile file) {
        Long personId = transactionService.getCurrentPersonId();
        String key = storageKeyFactory.storyboardCard(personId, extensionOf(file));
        return save(file, key);
    }

    public UploadResultResponse replaceFilmographyFile(String publicId, MultipartFile file) {
        Long personId = transactionService.getOwnedPersonId(publicId);
        String newKey = storageKeyFactory.filmography(personId, extensionOf(file));
        return saveAndApply(
                file,
                newKey,
                () -> transactionService.replaceFilmographyFile(publicId, newKey)
        );
    }

    private UploadResultResponse saveAndApply(
            MultipartFile file,
            String newKey,
            Runnable mutation
    ) {
        storageService.save(newKey, file);
        try {
            UploadResultResponse response = result(newKey);
            mutation.run();
            return response;
        } catch (RuntimeException exception) {
            compensateNewFile(newKey);
            throw exception;
        }
    }

    private UploadResultResponse save(MultipartFile file, String key) {
        storageService.save(key, file);
        try {
            return result(key);
        } catch (RuntimeException exception) {
            compensateNewFile(key);
            throw exception;
        }
    }

    private void compensateNewFile(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException exception) {
            log.error("Failed to compensate newly stored person media. key={}", key, exception);
        }
    }

    private UploadResultResponse result(String key) {
        return new UploadResultResponse(key, storageService.toPublicUrl(key));
    }

    private static String extensionOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "";
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index).toLowerCase();
    }
}
