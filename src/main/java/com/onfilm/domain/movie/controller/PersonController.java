package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
import com.onfilm.domain.movie.dto.FilmographyItemPrivacyRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertResponse;
import com.onfilm.domain.movie.dto.GalleryItemPrivacyRequest;
import com.onfilm.domain.movie.dto.GalleryItemResponse;
import com.onfilm.domain.movie.dto.GalleryReorderRequest;
import com.onfilm.domain.movie.dto.PrivacyUpdateRequest;
import com.onfilm.domain.movie.dto.StoryboardCardResponse;
import com.onfilm.domain.movie.dto.StoryboardSceneOrderRequest;
import com.onfilm.domain.movie.dto.StoryboardSceneRequest;
import com.onfilm.domain.movie.dto.StoryboardSceneResponse;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.domain.movie.service.MovieReadService;
import com.onfilm.domain.movie.service.MovieService;
import com.onfilm.domain.movie.service.PersonReadService;
import com.onfilm.domain.movie.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;
    private final PersonReadService personReadService;
    private final MovieReadService movieReadService;
    private final MovieService movieService;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;

    // =============================
    // PROFILE
    // =============================
    @GetMapping("/{publicId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String publicId) {
        ProfileResponse personResponse = personReadService.findProfileByPublicId(publicId);
        return ResponseEntity.ok(personResponse);
    }

    @PostMapping()
    public ResponseEntity<Long> createPerson(@RequestBody CreatePersonRequest request) {
        Long personId = personService.createPerson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(personId);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<String> updatePerson(@PathVariable String publicId,
                                             @RequestBody UpdatePersonRequest request) {
        personService.updatePerson(publicId, request);
        return ResponseEntity.ok(publicId);
    }

    // =============================
    // FILMOGRAPHY
    // =============================
    @GetMapping("/{publicId}/movies")
    public ResponseEntity<List<MovieCardResponse>> getFilmographyByPublicId(@PathVariable String publicId) {
        Long personId = personReadService.findPersonIdByPublicId(publicId);
        boolean isOwner = isOwner(personId);
        if (personReadService.isFilmographyPrivate(publicId) && !isOwner) {
            return ResponseEntity.ok(List.of());
        }

        List<MovieCardResponse> personFilmography = movieReadService.getFilmographyByPublicId(publicId);
        if (!isOwner) {
            personFilmography = personFilmography.stream()
                    .filter(item -> !item.isPrivate())
                    .toList();
        }
        return ResponseEntity.ok(personFilmography);
    }

    @GetMapping("/{publicId}/gallery")
    public ResponseEntity<List<GalleryItemResponse>> getGalleryByPublicId(@PathVariable String publicId) {
        Long personId = personReadService.findPersonIdByPublicId(publicId);
        boolean isOwner = isOwner(personId);
        if (personReadService.isGalleryPrivate(publicId) && !isOwner) {
            return ResponseEntity.ok(List.of());
        }

        List<Person.GalleryItem> items = personReadService.findGalleryItemsByPublicId(publicId);
        List<GalleryItemResponse> responses = items.stream()
                .filter(item -> isOwner || !item.isPrivate())
                .map(item -> new GalleryItemResponse(
                        item.getKey(),
                        storageService.toPublicUrl(item.getKey()),
                        item.isPrivate()
                ))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{publicId}/gallery")
    public ResponseEntity<Void> reorderGallery(@PathVariable String publicId,
                                               @RequestBody GalleryReorderRequest request) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<String> ordered = (request == null || request.keys() == null)
                ? List.of()
                : request.keys().stream()
                .map(this::toStorageKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();

        personReadService.reorderGallery(currentPersonId, ordered);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{publicId}/gallery")
    public ResponseEntity<Void> deleteGallery(@PathVariable String publicId,
                                              @RequestParam("key") String key) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String storageKey = toStorageKey(key);
        if (storageKey == null || storageKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        personReadService.removeGalleryImage(currentPersonId, storageKey);
        storageService.delete(storageKey);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{publicId}/filmography")
    public ResponseEntity<FilmographyUpsertResponse> upsertFilmography(
            @PathVariable String publicId,
            @RequestBody FilmographyUpsertRequest request
    ) {
        FilmographyUpsertResponse response = movieService.upsertFilmography(publicId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}/filmography/item/privacy")
    public ResponseEntity<Void> updateFilmographyItemPrivacy(
            @PathVariable String publicId,
            @RequestBody FilmographyItemPrivacyRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        movieService.updateFilmographyItemPrivacy(publicId, request.movieId(), request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{publicId}/filmography/privacy")
    public ResponseEntity<Void> updateFilmographyPrivacy(
            @PathVariable String publicId,
            @RequestBody PrivacyUpdateRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        personReadService.updateFilmographyPrivate(currentPersonId, request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{publicId}/gallery/privacy")
    public ResponseEntity<Void> updateGalleryPrivacy(
            @PathVariable String publicId,
            @RequestBody PrivacyUpdateRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        personReadService.updateGalleryPrivate(currentPersonId, request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{publicId}/gallery/item/privacy")
    public ResponseEntity<Void> updateGalleryItemPrivacy(
            @PathVariable String publicId,
            @RequestBody GalleryItemPrivacyRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String storageKey = toStorageKey(request.key());
        if (storageKey == null || storageKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        personReadService.updateGalleryItemPrivacy(currentPersonId, storageKey, request.isPrivate());
        return ResponseEntity.ok().build();
    }

    // =============================
    // STORYBOARD
    // =============================
    @GetMapping("/{publicId}/storyboard/scenes")
    public ResponseEntity<List<StoryboardSceneResponse>> getStoryboardScenes(@PathVariable String publicId) {
        Long personId = personReadService.findPersonIdByPublicId(publicId);
        if (!isOwner(personId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(personReadService.findStoryboardScenesByPublicId(publicId));
    }

    @PostMapping("/{publicId}/storyboard/scenes")
    public ResponseEntity<StoryboardSceneResponse> createStoryboardScene(
            @PathVariable String publicId,
            @RequestBody StoryboardSceneRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        StoryboardScene scene = personReadService.createStoryboardScene(currentPersonId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSceneResponse(scene));
    }

    @PutMapping("/{publicId}/storyboard/scenes/{sceneId}")
    public ResponseEntity<StoryboardSceneResponse> updateStoryboardScene(
            @PathVariable String publicId,
            @PathVariable Long sceneId,
            @RequestBody StoryboardSceneRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        StoryboardScene scene = personReadService.updateStoryboardScene(currentPersonId, sceneId, request);
        return ResponseEntity.ok(toSceneResponse(scene));
    }

    @PutMapping("/{publicId}/storyboard/scenes/order")
    public ResponseEntity<Void> reorderStoryboardScenes(
            @PathVariable String publicId,
            @RequestBody StoryboardSceneOrderRequest request
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Long> ordered = (request == null || request.sceneIds() == null)
                ? List.of()
                : request.sceneIds();
        personReadService.reorderStoryboardScenes(currentPersonId, ordered);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{publicId}/storyboard/scenes/{sceneId}")
    public ResponseEntity<Void> deleteStoryboardScene(
            @PathVariable String publicId,
            @PathVariable Long sceneId
    ) {
        Long currentPersonId = personReadService.findCurrentPersonId();
        Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
        if (!currentPersonId.equals(targetPersonId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        personReadService.deleteStoryboardScene(currentPersonId, sceneId);
        return ResponseEntity.noContent().build();
    }

    private boolean isOwner(Long personId) {
        try {
            Long currentPersonId = personReadService.findCurrentPersonId();
            return currentPersonId != null && currentPersonId.equals(personId);
        } catch (Exception e) {
            return false;
        }
    }

    // =============================
    // FILMOGRAPHY FILE
    // =============================
    @PostMapping("/{publicId}/filmography")
    public ResponseEntity<UploadResultResponse> uploadFilmography(@PathVariable String publicId,
                                                                  @RequestParam("file") MultipartFile file) {
        Long personId = personReadService.findPersonIdByPublicId(publicId);
        String oldKey = personReadService.findFilmographyKey(personId);
        String oldDeleteKey = toStorageKey(oldKey);

        String newKey = storageKeyFactory.filmography(personId, extOf(file));
        storageService.save(newKey, file);

        try {
            personReadService.updateFilmographyFile(personId, newKey);

            if (oldDeleteKey != null && !oldDeleteKey.isBlank() && !oldDeleteKey.equals(newKey)) {
                storageService.delete(oldDeleteKey);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UploadResultResponse(newKey, storageService.toPublicUrl(newKey)));
        } catch (Exception e) {
            try { storageService.delete(newKey); } catch (Exception ignore) {}
            throw e;
        }
    }

    @GetMapping("/{publicId}/filmography")
    public ResponseEntity<Void> getFilmography(@PathVariable String publicId) {
        String publicUrl = personReadService.findFilmographyPublicUrlByPublicId(publicId);
        if (publicUrl == null || publicUrl.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(publicUrl))
                .build();
    }

    private String extOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return (idx < 0) ? "" : name.substring(idx).toLowerCase();
    }

    private String toStorageKey(String value) {
        if (value == null || value.isBlank()) return null;
        String s = value.trim();

        if (s.startsWith("http://") || s.startsWith("https://")) {
            s = URI.create(s).getPath();
        }

        if (s.startsWith("/files/")) s = s.substring("/files/".length());
        if (s.startsWith("/")) s = s.substring(1);

        return s;
    }

    private StoryboardSceneResponse toSceneResponse(StoryboardScene scene) {
        if (scene == null) return null;
        List<StoryboardCardResponse> cards = new java.util.ArrayList<>();
        int cardIndex = 1;
        for (StoryboardCard card : scene.getCards()) {
            String key = card.getImageKey();
            String url = (key == null || key.isBlank()) ? null : storageService.toPublicUrl(key);
            cards.add(new StoryboardCardResponse(card.getId(), key, url, cardIndex));
            cardIndex += 1;
        }
        int sortOrder = 0;
        if (scene.getPerson() != null) {
            int sceneIndex = scene.getPerson().getStoryboardScenes().indexOf(scene);
            if (sceneIndex >= 0) sortOrder = sceneIndex + 1;
        }
        return new StoryboardSceneResponse(
                scene.getId(),
                scene.getTitle(),
                scene.getScriptHtml(),
                sortOrder,
                cards
        );
    }
}
