package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonMediaService {

    private final CurrentPersonProvider currentPersonProvider;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;
    private final StorageFileDeletionPublisher deletionPublisher;

    public UploadResultResponse replaceProfileImage(MultipartFile file) {
        Person person = currentPersonProvider.getRequired();
        String oldKey = person.getProfileImageKey();
        String newKey = storageKeyFactory.profileAvatar(person.getId(), extensionOf(file));
        return saveAndApply(file, newKey, () -> person.changeProfileImageKey(newKey), oldKey);
    }

    public void deleteProfileImage() {
        Person person = currentPersonProvider.getRequired();
        String oldKey = person.getProfileImageKey();
        person.changeProfileImageKey(null);
        deletionPublisher.publish(oldKey);
    }

    public UploadResultResponse addGalleryImage(MultipartFile file) {
        Person person = currentPersonProvider.getRequired();
        String key = storageKeyFactory.gallery(person.getId(), extensionOf(file));
        return saveAndApply(file, key, () -> person.addGalleryImageKey(key), null);
    }

    public UploadResultResponse uploadStoryboardImage(MultipartFile file) {
        Person person = currentPersonProvider.getRequired();
        String key = storageKeyFactory.storyboardCard(person.getId(), extensionOf(file));
        storageService.save(key, file);
        return result(key);
    }

    public UploadResultResponse replaceFilmographyFile(String publicId, MultipartFile file) {
        Person person = currentPersonProvider.getRequired(publicId);
        String oldKey = person.getFilmographyFileKey();
        String newKey = storageKeyFactory.filmography(person.getId(), extensionOf(file));
        return saveAndApply(file, newKey, () -> person.changeFilmographyFileKey(newKey), oldKey);
    }

    private UploadResultResponse saveAndApply(
            MultipartFile file,
            String newKey,
            Runnable mutation,
            String oldKey
    ) {
        storageService.save(newKey, file);
        try {
            mutation.run();
            if (oldKey != null && !oldKey.equals(newKey)) {
                deletionPublisher.publish(oldKey);
            }
            return result(newKey);
        } catch (RuntimeException exception) {
            try {
                storageService.delete(newKey);
            } catch (RuntimeException ignored) {
                // 새 파일 보상 삭제는 최선 노력으로 수행한다.
            }
            throw exception;
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
