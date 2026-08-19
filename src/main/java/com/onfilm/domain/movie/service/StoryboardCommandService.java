package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardProjectNotFoundException;
import com.onfilm.domain.common.error.exception.StoryboardSceneNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.event.StorageFilesDeleteEvent;
import com.onfilm.domain.file.service.StorageKeyPolicy;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final StorageKeyPolicy storageKeyPolicy;
    private final ApplicationEventPublisher eventPublisher;

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
        deleteCardFiles(person.getId(), project);
        person.removeStoryboardProject(project);
    }

    public StoryboardScene createScene(Long projectId, StoryboardSceneRequest request) {
        StoryboardSceneRequest requiredRequest = require(request, "request");
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        List<StoryboardScene.CardChange> cardChanges = toCardChanges(
                person.getId(),
                requiredRequest.cards()
        );
        StoryboardScene scene = project.addScene(
                requiredRequest.title(),
                requiredRequest.scriptHtml()
        );
        StoryboardScene.CardReplacementResult result = scene.replaceCards(
                cardChanges
        );
        deleteFiles(person.getId(), result.obsoleteImageKeys());
        return scene;
    }

    public StoryboardScene updateScene(
            Long projectId,
            Long sceneId,
            StoryboardSceneRequest request
    ) {
        StoryboardSceneRequest requiredRequest = require(request, "request");
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        StoryboardScene scene = findScene(project, sceneId);
        List<StoryboardScene.CardChange> cardChanges = toCardChanges(
                person.getId(),
                requiredRequest.cards()
        );
        scene.changeContent(requiredRequest.title(), requiredRequest.scriptHtml());
        StoryboardScene.CardReplacementResult result = scene.replaceCards(
                cardChanges
        );
        deleteFiles(person.getId(), result.obsoleteImageKeys());
        return scene;
    }

    public void deleteScene(Long projectId, Long sceneId) {
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        StoryboardScene scene = findScene(project, sceneId);
        deleteCardFiles(person.getId(), scene);
        project.removeScene(scene);
    }

    public void reorderScenes(Long projectId, List<Long> sceneIds) {
        StoryboardProject project = findProject(findCurrentPerson(), projectId);
        project.reorderScenes(sceneIds);
    }

    private List<StoryboardScene.CardChange> toCardChanges(
            Long personId,
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
                    storageKeyPolicy.validateStoryboardCardKey(
                            personId,
                            requiredCardRequest.imageKey()
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

    private void deleteCardFiles(Long personId, StoryboardProject project) {
        List<String> imageKeys = project.getScenes().stream()
                .flatMap(scene -> scene.getCards().stream())
                .map(StoryboardCard::getImageKey)
                .toList();
        deleteFiles(personId, imageKeys);
    }

    private void deleteCardFiles(Long personId, StoryboardScene scene) {
        List<String> imageKeys = scene.getCards().stream()
                .map(StoryboardCard::getImageKey)
                .toList();
        deleteFiles(personId, imageKeys);
    }

    private void deleteFiles(Long personId, List<String> keys) {
        List<String> keysToDelete = keys.stream()
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        keysToDelete.forEach(
                key -> storageKeyPolicy.validateStoryboardCardKey(personId, key)
        );
        if (!keysToDelete.isEmpty()) {
            eventPublisher.publishEvent(new StorageFilesDeleteEvent(keysToDelete));
        }
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
