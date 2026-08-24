package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.event.StorageFileDeletionPublisher;
import com.onfilm.domain.movie.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GalleryCommandService {

    private final CurrentPersonProvider currentPersonProvider;
    private final StorageFileDeletionPublisher deletionPublisher;

    public void removeImage(String publicId, String key) {
        Person person = currentPersonProvider.getRequired(publicId);
        person.removeGalleryImageKey(key);
        deletionPublisher.publish(key);
    }

    public void reorder(String publicId, List<String> orderedKeys) {
        currentPersonProvider.getRequired(publicId).reorderGallery(orderedKeys);
    }

    public void changeGalleryPrivacy(String publicId, boolean isPrivate) {
        currentPersonProvider.getRequired(publicId).changeGalleryPrivate(isPrivate);
    }

    public void changeItemPrivacy(String publicId, String key, boolean isPrivate) {
        currentPersonProvider.getRequired(publicId).changeGalleryItemPrivacy(key, isPrivate);
    }
}
