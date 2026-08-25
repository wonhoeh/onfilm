package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.domain.movie.service.*;
import jakarta.validation.Valid;
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
    private final PersonCommandService personCommandService;
    private final PersonQueryService personQueryService;
    private final GalleryQueryService galleryQueryService;
    private final GalleryCommandService galleryCommandService;
    private final FilmographyQueryService filmographyQueryService;
    private final FilmographyCommandService filmographyCommandService;
    private final PersonMediaService personMediaService;
    private final StoryboardQueryService storyboardQueryService;
    private final StoryboardCommandService storyboardCommandService;
    private final StoryboardResponseMapper storyboardResponseMapper;

    @GetMapping("/{publicId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String publicId) {
        return ResponseEntity.ok(personQueryService.findProfileByPublicId(publicId));
    }

    @PostMapping
    public ResponseEntity<Long> createPerson(@Valid @RequestBody CreatePersonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personCommandService.initializeProfile(request));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<String> updatePerson(@PathVariable String publicId,
                                               @Valid @RequestBody UpdatePersonRequest request) {
        personCommandService.updateProfile(publicId, request);
        return ResponseEntity.ok(publicId);
    }

    @GetMapping("/{publicId}/movies")
    public ResponseEntity<List<MovieCardResponse>> getFilmographyByPublicId(@PathVariable String publicId) {
        return ResponseEntity.ok(filmographyQueryService.findVisibleFilmography(publicId));
    }

    @GetMapping("/{publicId}/gallery")
    public ResponseEntity<List<GalleryItemResponse>> getGalleryByPublicId(@PathVariable String publicId) {
        return ResponseEntity.ok(galleryQueryService.findVisibleGallery(publicId));
    }

    @PutMapping("/{publicId}/gallery")
    public ResponseEntity<Void> reorderGallery(@PathVariable String publicId,
                                               @Valid @RequestBody GalleryReorderRequest request) {
        galleryCommandService.reorder(publicId, request.keys());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{publicId}/gallery")
    public ResponseEntity<Void> deleteGallery(@PathVariable String publicId,
                                              @RequestParam("key") String key) {
        galleryCommandService.removeImage(publicId, key);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{publicId}/filmography")
    public ResponseEntity<FilmographyUpsertResponse> upsertFilmography(
            @PathVariable String publicId,
            @Valid @RequestBody FilmographyUpsertRequest request) {
        return ResponseEntity.ok(filmographyCommandService.replace(publicId, request));
    }

    @PutMapping("/{publicId}/filmography/item/privacy")
    public ResponseEntity<Void> updateFilmographyItemPrivacy(
            @PathVariable String publicId,
            @Valid @RequestBody FilmographyItemPrivacyRequest request) {
        filmographyCommandService.changeItemPrivacy(publicId, request.movieId(), request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{publicId}/filmography/privacy")
    public ResponseEntity<Void> updateFilmographyPrivacy(@PathVariable String publicId,
                                                         @Valid @RequestBody PrivacyUpdateRequest request) {
        filmographyCommandService.changeFilmographyPrivacy(publicId, request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{publicId}/gallery/privacy")
    public ResponseEntity<Void> updateGalleryPrivacy(@PathVariable String publicId,
                                                     @Valid @RequestBody PrivacyUpdateRequest request) {
        galleryCommandService.changeGalleryPrivacy(publicId, request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{publicId}/gallery/item/privacy")
    public ResponseEntity<Void> updateGalleryItemPrivacy(
            @PathVariable String publicId,
            @Valid @RequestBody GalleryItemPrivacyRequest request) {
        galleryCommandService.changeItemPrivacy(publicId, request.key(), request.isPrivate());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{publicId}/storyboard/projects")
    public ResponseEntity<List<StoryboardProjectSummaryResponse>> getStoryboardProjects(@PathVariable String publicId) {
        return ResponseEntity.ok(storyboardQueryService.findProjectsByPublicId(publicId));
    }

    @PostMapping("/{publicId}/storyboard/projects")
    public ResponseEntity<StoryboardProjectResponse> createStoryboardProject(
            @PathVariable String publicId,
            @Valid @RequestBody StoryboardProjectRequest request) {
        StoryboardProject project = storyboardCommandService.createProject(publicId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(storyboardResponseMapper.toProjectResponse(project));
    }

    @GetMapping("/{publicId}/storyboard/projects/{projectId}")
    public ResponseEntity<StoryboardProjectResponse> getStoryboardProject(
            @PathVariable String publicId, @PathVariable Long projectId) {
        return ResponseEntity.ok(storyboardQueryService.findProjectByPublicId(publicId, projectId));
    }

    @PutMapping("/{publicId}/storyboard/projects/{projectId}")
    public ResponseEntity<StoryboardProjectResponse> updateStoryboardProject(
            @PathVariable String publicId, @PathVariable Long projectId,
            @Valid @RequestBody StoryboardProjectRequest request) {
        StoryboardProject project = storyboardCommandService.updateProject(publicId, projectId, request);
        return ResponseEntity.ok(storyboardResponseMapper.toProjectResponse(project));
    }

    @DeleteMapping("/{publicId}/storyboard/projects/{projectId}")
    public ResponseEntity<Void> deleteStoryboardProject(@PathVariable String publicId,
                                                        @PathVariable Long projectId) {
        storyboardCommandService.deleteProject(publicId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/storyboard/projects/{projectId}/scenes")
    public ResponseEntity<StoryboardSceneResponse> createStoryboardScene(
            @PathVariable String publicId, @PathVariable Long projectId,
            @Valid @RequestBody StoryboardSceneRequest request) {
        StoryboardScene scene = storyboardCommandService.createScene(publicId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(storyboardResponseMapper.toSceneResponse(scene));
    }

    @PutMapping("/{publicId}/storyboard/projects/{projectId}/scenes/{sceneId}")
    public ResponseEntity<StoryboardSceneResponse> updateStoryboardScene(
            @PathVariable String publicId, @PathVariable Long projectId, @PathVariable Long sceneId,
            @Valid @RequestBody StoryboardSceneRequest request) {
        StoryboardScene scene = storyboardCommandService.updateScene(publicId, projectId, sceneId, request);
        return ResponseEntity.ok(storyboardResponseMapper.toSceneResponse(scene));
    }

    @PutMapping("/{publicId}/storyboard/projects/{projectId}/scenes/order")
    public ResponseEntity<Void> reorderStoryboardScenes(
            @PathVariable String publicId, @PathVariable Long projectId,
            @Valid @RequestBody StoryboardSceneOrderRequest request) {
        storyboardCommandService.reorderScenes(publicId, projectId, request.sceneIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{publicId}/storyboard/projects/{projectId}/scenes/{sceneId}")
    public ResponseEntity<Void> deleteStoryboardScene(
            @PathVariable String publicId, @PathVariable Long projectId, @PathVariable Long sceneId) {
        storyboardCommandService.deleteScene(publicId, projectId, sceneId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/filmography")
    public ResponseEntity<UploadResultResponse> uploadFilmography(@PathVariable String publicId,
                                                                  @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personMediaService.replaceFilmographyFile(publicId, file));
    }

    @GetMapping("/{publicId}/filmography")
    public ResponseEntity<Void> getFilmography(@PathVariable String publicId) {
        String publicUrl = personQueryService.findFilmographyPublicUrlByPublicId(publicId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(publicUrl)).build();
    }
}
