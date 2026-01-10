package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertResponse;
import com.onfilm.domain.movie.dto.GalleryReorderRequest;
import com.onfilm.domain.movie.dto.UploadResultResponse;
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
        List<MovieCardResponse> personFilmography = movieReadService.getFilmographyByPublicId(publicId);
        return ResponseEntity.ok(personFilmography);
    }

    @GetMapping("/{publicId}/gallery")
    public ResponseEntity<List<String>> getGalleryByPublicId(@PathVariable String publicId) {
        List<String> keys = personReadService.findGalleryKeysByPublicId(publicId);
        List<String> urls = keys.stream()
                .map(storageService::toPublicUrl)
                .toList();
        return ResponseEntity.ok(urls);
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
}
