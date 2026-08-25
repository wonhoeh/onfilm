package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.movie.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonMediaTransactionService {

    private final CurrentPersonProvider currentPersonProvider;
    private final StorageFileDeletionPublisher deletionPublisher;

    @Transactional(readOnly = true)
    public Long getCurrentPersonId() {
        return currentPersonProvider.getRequiredId();
    }

    @Transactional(readOnly = true)
    public Long getOwnedPersonId(String publicId) {
        return currentPersonProvider.getRequired(publicId).getId();
    }

    @Transactional
    public void replaceProfileImage(String newKey) {
        Person person = currentPersonProvider.getRequired();
        String oldKey = person.getProfileImageKey();
        person.changeProfileImageKey(newKey);
        publishReplacedKey(oldKey, newKey);
    }

    @Transactional
    public void deleteProfileImage() {
        Person person = currentPersonProvider.getRequired();
        String oldKey = person.getProfileImageKey();
        person.changeProfileImageKey(null);
        deletionPublisher.publish(oldKey);
    }

    @Transactional
    public void addGalleryImage(String key) {
        currentPersonProvider.getRequired().addGalleryImageKey(key);
    }

    @Transactional
    public void replaceFilmographyFile(String publicId, String newKey) {
        Person person = currentPersonProvider.getRequired(publicId);
        String oldKey = person.getFilmographyFileKey();
        person.changeFilmographyFileKey(newKey);
        publishReplacedKey(oldKey, newKey);
    }

    private void publishReplacedKey(String oldKey, String newKey) {
        if (oldKey != null && !oldKey.equals(newKey)) {
            deletionPublisher.publish(oldKey);
        }
    }
}
