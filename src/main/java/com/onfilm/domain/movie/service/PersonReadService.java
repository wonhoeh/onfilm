package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardSceneNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonReadService {

    private final PersonRepository personRepository;
    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public ProfileResponse findProfileByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        String key = person.getProfileImageUrl();
        String publicUrl = (key == null || key.isBlank()) ? null : storageService.toPublicUrl(key);

        return ProfileResponse.from(person, publicUrl);
    }

    public Long findCurrentPersonId() {
        String principal = SecurityUtil.currentPrincipal();

        Long userId;
        try {
            userId = Long.valueOf(principal);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("INVALID_PRINCIPAL");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND"));

        if (user.getPerson() == null) {
            throw new IllegalStateException("PERSON_NOT_LINKED");
        }

        return user.getPerson().getId();
    }

    public String findProfileImageKey(Long personId) {
        return personRepository.findProfileImageKeyById(personId)
                .orElse(null);
    }

    public Long findPersonIdByPublicId(String publicId) {
        return personRepository.findByPublicId(publicId)
                .map(Person::getId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
    }

    public String findFilmographyKey(Long personId) {
        return personRepository.findFilmographyKeyById(personId)
                .orElse(null);
    }

    public String findFilmographyPublicUrlByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        String key = person.getFilmographyFileKey();
        return (key == null || key.isBlank()) ? null : storageService.toPublicUrl(key);
    }

    public boolean isFilmographyPrivate(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return person.isFilmographyPrivate();
    }

    public boolean isGalleryPrivate(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return person.isGalleryPrivate();
    }

    public List<Person.GalleryItem> findGalleryItemsByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return new ArrayList<>(person.getGalleryItems());
    }

    public List<StoryboardProjectSummaryResponse> findStoryboardProjectsByPublicId(String publicId) {
        // [k6 N+1 테스트] fetch join 제거 버전 (N+1 발생)
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        // [k6 N+1 테스트] fetch join 적용 버전 (정상)
        // Person person = personRepository.findByPublicIdWithStoryboards(publicId)
        //         .orElseThrow(() -> new PersonNotFoundException(publicId));
        List<StoryboardProjectSummaryResponse> responses = new ArrayList<>();
        for (StoryboardProject project : person.getStoryboardProjects()) {
            String preview = extractPreview(project);
            responses.add(new StoryboardProjectSummaryResponse(
                    project.getId(),
                    project.getTitle(),
                    preview,
                    project.getScenes().size()
            ));
        }
        return responses;
    }

    public StoryboardProject findStoryboardProjectByPublicId(String publicId, Long projectId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        initializeProject(project);
        return project;
    }

    // =============================
    // WRITE — 현재 유저 소유 데이터만 수정
    // =============================

    @Transactional
    public void updatePersonProfileImage(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeProfileImageUrl(key);
    }

    @Transactional
    public void addPersonGalleryImage(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.addGalleryImageKey(key);
    }

    @Transactional
    public void updateMovieThumbnail(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        movie.changeThumbnailUrl(key);
    }

    @Transactional
    public void updateMovieFile(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        movie.changeMovieUrl(key);
    }

    @Transactional
    public void addMovieTrailer(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        movie.addTrailer(key);
    }

    public boolean canEditMovie(Long personId, Long movieId) {
        return moviePersonRepository.findByPersonIdAndMovieId(personId, movieId) != null;
    }

    @Transactional
    public void updateFilmographyFile(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeFilmographyFileKey(key);
    }

    @Transactional
    public StoryboardProject createStoryboardProject(StoryboardProjectRequest request) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String title = (request == null || request.title() == null || request.title().isBlank())
                ? "새 스토리보드"
                : request.title().trim();
        StoryboardProject project = new StoryboardProject(title);
        project.attachPerson(person);
        person.getStoryboardProjects().add(project);
        return project;
    }

    @Transactional
    public StoryboardProject updateStoryboardProject(Long projectId, StoryboardProjectRequest request) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        if (request != null && request.title() != null && !request.title().isBlank()) {
            project.updateTitle(request.title().trim());
        }
        return project;
    }

    @Transactional
    public void deleteStoryboardProject(Long projectId) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        for (StoryboardScene scene : project.getScenes()) {
            for (StoryboardCard card : scene.getCards()) {
                String key = card.getImageKey();
                if (key != null && !key.isBlank()) storageService.delete(key);
            }
        }
        person.getStoryboardProjects().remove(project);
    }

    @Transactional
    public StoryboardScene createStoryboardScene(Long projectId, StoryboardSceneRequest request) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        String title = (request == null) ? null : request.title();
        String script = (request == null) ? null : request.scriptHtml();
        StoryboardScene scene = new StoryboardScene(title, script);
        scene.attachProject(project);

        List<String> imageKeys = (request == null || request.cards() == null)
                ? List.of()
                : request.cards().stream()
                .map(card -> card.imageKey())
                .filter(key -> key != null && !key.isBlank())
                .toList();

        for (String key : imageKeys) {
            StoryboardCard card = new StoryboardCard(key);
            card.attachScene(scene);
            scene.getCards().add(card);
        }

        project.getScenes().add(scene);
        return scene;
    }

    @Transactional
    public StoryboardScene updateStoryboardScene(Long projectId, Long sceneId, StoryboardSceneRequest request) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        StoryboardScene scene = findStoryboardScene(project, sceneId);
        if (request != null) {
            scene.updateTitle(request.title());
            scene.updateScriptHtml(request.scriptHtml());
        }

        Map<Long, StoryboardCard> existing = new LinkedHashMap<>();
        for (StoryboardCard card : scene.getCards()) {
            existing.put(card.getId(), card);
        }

        List<StoryboardCard> next = new ArrayList<>();
        Set<Long> kept = new HashSet<>();

        if (request != null && request.cards() != null) {
            for (StoryboardCardRequest cardReq : request.cards()) {
                StoryboardCard card = null;
                if (cardReq.cardId() != null) {
                    card = existing.get(cardReq.cardId());
                }
                if (card == null) {
                    card = new StoryboardCard(cardReq.imageKey());
                    card.attachScene(scene);
                } else {
                    String oldKey = card.getImageKey();
                    String newKey = cardReq.imageKey();
                    if (newKey == null || newKey.isBlank()) {
                        if (oldKey != null && !oldKey.isBlank()) storageService.delete(oldKey);
                        card.updateImageKey(null);
                    } else if (!newKey.equals(oldKey)) {
                        card.updateImageKey(newKey);
                        if (oldKey != null && !oldKey.isBlank()) storageService.delete(oldKey);
                    }
                    kept.add(card.getId());
                }
                next.add(card);
            }
        }

        for (StoryboardCard card : scene.getCards()) {
            if (card.getId() == null || !kept.contains(card.getId())) {
                String key = card.getImageKey();
                if (key != null && !key.isBlank()) storageService.delete(key);
            }
        }

        scene.getCards().clear();
        scene.getCards().addAll(next);
        return scene;
    }

    @Transactional
    public void deleteStoryboardScene(Long projectId, Long sceneId) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        StoryboardScene scene = findStoryboardScene(project, sceneId);
        for (StoryboardCard card : scene.getCards()) {
            String key = card.getImageKey();
            if (key != null && !key.isBlank()) storageService.delete(key);
        }
        project.getScenes().remove(scene);
    }

    @Transactional
    public void reorderStoryboardScenes(Long projectId, List<Long> sceneIds) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        StoryboardProject project = findStoryboardProject(person, projectId);
        if (sceneIds == null) return;

        Map<Long, StoryboardScene> byId = new LinkedHashMap<>();
        for (StoryboardScene scene : project.getScenes()) {
            byId.putIfAbsent(scene.getId(), scene);
        }

        List<StoryboardScene> reordered = new ArrayList<>();
        for (Long id : sceneIds) {
            StoryboardScene scene = byId.remove(id);
            if (scene != null) reordered.add(scene);
        }
        reordered.addAll(byId.values());

        project.getScenes().clear();
        project.getScenes().addAll(reordered);
    }

    @Transactional
    public void clearProfileImage() {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeProfileImageUrl(null);
    }

    @Transactional
    public void deleteMovieFiles(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));

        String thumbnailKey = movie.getThumbnailUrl();
        String movieKey = movie.getMovieUrl();

        List<String> trailerKeys = movie.getTrailers().stream()
                .map(Trailer::getUrl)
                .filter(k -> k != null && !k.isBlank())
                .toList();

        movie.clearThumbnailUrl();
        movie.clearMovieUrl();
        movie.clearTrailers();

        if (thumbnailKey != null && !thumbnailKey.isBlank()) storageService.delete(thumbnailKey);
        if (movieKey != null && !movieKey.isBlank()) storageService.delete(movieKey);
        for (String key : trailerKeys) {
            storageService.delete(key);
        }
    }

    @Transactional
    public void deleteMovieThumbnail(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        String key = movie.getThumbnailUrl();
        movie.clearThumbnailUrl();
        if (key != null && !key.isBlank()) storageService.delete(key);
    }

    @Transactional
    public void deleteMovieFile(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        String key = movie.getMovieUrl();
        movie.clearMovieUrl();
        if (key != null && !key.isBlank()) storageService.delete(key);
    }

    @Transactional
    public void deleteMovieTrailers(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        List<String> keys = movie.getTrailers().stream()
                .map(Trailer::getUrl)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        movie.clearTrailers();
        for (String key : keys) {
            storageService.delete(key);
        }
    }

    @Transactional
    public void removeGalleryImage(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.removeGalleryImageKey(key);
    }

    @Transactional
    public void reorderGallery(java.util.List<String> orderedKeys) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.reorderGallery(orderedKeys);
    }

    @Transactional
    public void updateFilmographyPrivate(boolean isPrivate) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeFilmographyPrivate(isPrivate);
    }

    @Transactional
    public void updateGalleryPrivate(boolean isPrivate) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeGalleryPrivate(isPrivate);
    }

    @Transactional
    public void updateGalleryItemPrivacy(String key, boolean isPrivate) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.updateGalleryItemPrivacy(key, isPrivate);
    }

    private StoryboardProject findStoryboardProject(Person person, Long projectId) {
        return person.getStoryboardProjects().stream()
                .filter(project -> project.getId().equals(projectId))
                .findFirst()
                .orElseThrow(() -> new StoryboardProjectNotFoundException(projectId));
    }

    private StoryboardScene findStoryboardScene(StoryboardProject project, Long sceneId) {
        return project.getScenes().stream()
                .filter(scene -> scene.getId().equals(sceneId))
                .findFirst()
                .orElseThrow(() -> new StoryboardSceneNotFoundException(sceneId));
    }

    private String extractPreview(StoryboardProject project) {
        for (StoryboardScene scene : project.getScenes()) {
            String script = scene.getScriptHtml();
            if (script == null || script.isBlank()) continue;
            String cleaned = script.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            if (!cleaned.isBlank()) {
                return cleaned.length() > 160 ? cleaned.substring(0, 160) + "…" : cleaned;
            }
        }
        return "";
    }

    private void initializeProject(StoryboardProject project) {
        if (project == null) return;
        for (StoryboardScene scene : project.getScenes()) {
            scene.getCards().size();
        }
    }
}
