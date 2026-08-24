package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.GalleryItemResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GalleryQueryService {

    private final PersonRepository personRepository;
    private final CurrentPersonProvider currentPersonProvider;
    private final StorageService storageService;

    public List<GalleryItemResponse> findVisibleGallery(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        boolean owner = currentPersonProvider.isCurrentPerson(person.getId());
        if (person.isGalleryPrivate() && !owner) {
            return List.of();
        }
        return person.getGalleryItems().stream()
                .filter(item -> owner || !item.isPrivate())
                .map(item -> new GalleryItemResponse(
                        item.getKey(),
                        storageService.toPublicUrl(item.getKey()),
                        item.isPrivate()
                ))
                .toList();
    }
}
