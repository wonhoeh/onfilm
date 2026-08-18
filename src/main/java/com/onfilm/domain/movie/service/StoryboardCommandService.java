package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardSceneNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.StoryboardCardRequest;
import com.onfilm.domain.movie.dto.StoryboardProjectRequest;
import com.onfilm.domain.movie.dto.StoryboardSceneRequest;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class StoryboardCommandService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public StoryboardProject createProject(StoryboardProjectRequest request) {
        StoryboardProjectRequest requiredRequest = require(request, "request");
        Person person = findCurrentPerson();
        return person.addStoryboardProject(requiredRequest.title());
    }

    public StoryboardProject updateProject(Long projectId, StoryboardProjectRequest request) {
        StoryboardProjectRequest requiredRequest = require(request, "request");
        StoryboardProject project = findProject(findCurrentPerson(), projectId);
        project.changeTitle(requiredRequest.title());
        return project;
    }

    public void deleteProject(Long projectId) {
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        deleteCardFiles(project);
        person.removeStoryboardProject(project);
    }

    public StoryboardScene createScene(Long projectId, StoryboardSceneRequest request) {
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        String title = request == null ? null : request.title();
        String script = request == null ? null : request.scriptHtml();
        StoryboardScene scene = new StoryboardScene(title, script);

        List<StoryboardCardRequest> cards = request == null || request.cards() == null
                ? List.of()
                : request.cards();
        for (StoryboardCardRequest cardRequest : cards) {
            if (cardRequest.imageKey() == null || cardRequest.imageKey().isBlank()) {
                continue;
            }

            StoryboardCard card = new StoryboardCard(cardRequest.imageKey());
            card.attachScene(scene);
            scene.getCards().add(card);
        }

        project.addScene(scene);
        return scene;
    }

    public StoryboardScene updateScene(
            Long projectId,
            Long sceneId,
            StoryboardSceneRequest request
    ) {
        StoryboardProject project = findProject(findCurrentPerson(), projectId);
        StoryboardScene scene = findScene(project, sceneId);
        if (request != null) {
            scene.changeTitle(request.title());
            scene.changeScriptHtml(request.scriptHtml());
        }

        replaceCards(scene, request == null ? null : request.cards());
        return scene;
    }

    public void deleteScene(Long projectId, Long sceneId) {
        StoryboardProject project = findProject(findCurrentPerson(), projectId);
        StoryboardScene scene = findScene(project, sceneId);
        deleteCardFiles(scene);
        project.removeScene(scene);
    }

    public void reorderScenes(Long projectId, List<Long> sceneIds) {
        StoryboardProject project = findProject(findCurrentPerson(), projectId);
        project.reorderScenes(sceneIds);
    }

    private void replaceCards(StoryboardScene scene, List<StoryboardCardRequest> cardRequests) {
        Map<Long, StoryboardCard> existing = new LinkedHashMap<>();
        for (StoryboardCard card : scene.getCards()) {
            existing.put(card.getId(), card);
        }

        List<StoryboardCard> next = new ArrayList<>();
        Set<Long> kept = new HashSet<>();
        if (cardRequests != null) {
            for (StoryboardCardRequest cardRequest : cardRequests) {
                StoryboardCard card = cardRequest.cardId() == null
                        ? null
                        : existing.get(cardRequest.cardId());
                if (card == null) {
                    card = new StoryboardCard(cardRequest.imageKey());
                    card.attachScene(scene);
                } else {
                    changeCardImage(card, cardRequest.imageKey());
                    kept.add(card.getId());
                }
                next.add(card);
            }
        }

        for (StoryboardCard card : scene.getCards()) {
            if (card.getId() == null || !kept.contains(card.getId())) {
                deleteFile(card.getImageKey());
            }
        }

        scene.getCards().clear();
        scene.getCards().addAll(next);
    }

    private void changeCardImage(StoryboardCard card, String newKey) {
        String oldKey = card.getImageKey();
        if (newKey == null || newKey.isBlank()) {
            deleteFile(oldKey);
            card.changeImageKey(null);
            return;
        }
        if (!newKey.equals(oldKey)) {
            card.changeImageKey(newKey);
            deleteFile(oldKey);
        }
    }

    private Person findCurrentPerson() {
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

        Long personId = user.getPerson().getId();
        return personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
    }

    private StoryboardProject findProject(Person person, Long projectId) {
        return person.getStoryboardProjects().stream()
                .filter(project -> Objects.equals(project.getId(), projectId))
                .findFirst()
                .orElseThrow(() -> new StoryboardProjectNotFoundException(projectId));
    }

    private StoryboardScene findScene(StoryboardProject project, Long sceneId) {
        return project.getScenes().stream()
                .filter(scene -> Objects.equals(scene.getId(), sceneId))
                .findFirst()
                .orElseThrow(() -> new StoryboardSceneNotFoundException(sceneId));
    }

    private void deleteCardFiles(StoryboardProject project) {
        for (StoryboardScene scene : project.getScenes()) {
            deleteCardFiles(scene);
        }
    }

    private void deleteCardFiles(StoryboardScene scene) {
        for (StoryboardCard card : scene.getCards()) {
            deleteFile(card.getImageKey());
        }
    }

    private void deleteFile(String key) {
        if (key != null && !key.isBlank()) {
            storageService.delete(key);
        }
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
