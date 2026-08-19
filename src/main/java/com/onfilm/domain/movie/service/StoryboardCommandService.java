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

import java.util.List;
import java.util.Objects;

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
        StoryboardSceneRequest requiredRequest = require(request, "request");
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        StoryboardScene scene = project.addScene(
                requiredRequest.title(),
                requiredRequest.scriptHtml()
        );
        StoryboardScene.CardReplacementResult result = scene.replaceCards(
                toCardChanges(requiredRequest.cards())
        );
        deleteFiles(result.obsoleteImageKeys());
        return scene;
    }

    public StoryboardScene updateScene(
            Long projectId,
            Long sceneId,
            StoryboardSceneRequest request
    ) {
        StoryboardSceneRequest requiredRequest = require(request, "request");
        StoryboardProject project = findProject(findCurrentPerson(), projectId);
        StoryboardScene scene = findScene(project, sceneId);
        scene.changeContent(requiredRequest.title(), requiredRequest.scriptHtml());
        StoryboardScene.CardReplacementResult result = scene.replaceCards(
                toCardChanges(requiredRequest.cards())
        );
        deleteFiles(result.obsoleteImageKeys());
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

    private List<StoryboardScene.CardChange> toCardChanges(
            List<StoryboardCardRequest> cardRequests
    ) {
        List<StoryboardCardRequest> requiredRequests = require(
                cardRequests,
                "cards"
        );
        return requiredRequests.stream()
                .map(cardRequest -> {
                    StoryboardCardRequest requiredCardRequest = require(
                            cardRequest,
                            "cardRequest"
                    );
                    return new StoryboardScene.CardChange(
                            requiredCardRequest.cardId(),
                            requiredCardRequest.imageKey()
                    );
                })
                .toList();
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

    private void deleteFiles(List<String> keys) {
        keys.forEach(this::deleteFile);
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
