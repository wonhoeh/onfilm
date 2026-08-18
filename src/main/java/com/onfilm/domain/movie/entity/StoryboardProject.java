package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryboardProject {

    public static final int TITLE_MAX_LENGTH = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = TITLE_MAX_LENGTH, nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    private List<StoryboardScene> scenes = new ArrayList<>();

    private StoryboardProject(String title) {
        this.title = requireTitle(title);
    }

    public static StoryboardProject create(String title) {
        return new StoryboardProject(title);
    }

    void attachPerson(Person person) {
        if (person == null) {
            throw new IllegalArgumentException("person is required");
        }
        if (this.person != null && this.person != person) {
            throw new IllegalStateException(
                    "storyboardProject already belongs to another person"
            );
        }
        this.person = person;
    }

    void detachPerson(Person person) {
        if (this.person == person) {
            this.person = null;
        }
    }

    public void changeTitle(String title) {
        this.title = requireTitle(title);
    }

    public void addScene(StoryboardScene scene) {
        StoryboardScene requiredScene = require(scene, "scene");
        if (scenes.contains(requiredScene)) {
            throw new IllegalArgumentException("duplicate storyboard scene");
        }

        requiredScene.attachProject(this);
        scenes.add(requiredScene);
    }

    public void removeScene(StoryboardScene scene) {
        StoryboardScene requiredScene = require(scene, "scene");
        if (!scenes.remove(requiredScene)) {
            throw new IllegalArgumentException("scene does not belong to project");
        }

        requiredScene.detachProject(this);
    }

    public void reorderScenes(List<Long> sceneIds) {
        List<Long> requiredSceneIds = require(sceneIds, "sceneIds");
        if (requiredSceneIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sceneId must not be null");
        }

        Set<Long> requestedIds = new LinkedHashSet<>(requiredSceneIds);
        if (requestedIds.size() != requiredSceneIds.size()) {
            throw new IllegalArgumentException("duplicate sceneId");
        }

        Map<Long, StoryboardScene> existingById = new LinkedHashMap<>();
        for (StoryboardScene scene : scenes) {
            Long sceneId = scene.getId();
            if (sceneId == null) {
                throw new IllegalStateException("cannot reorder unsaved storyboard scene");
            }
            existingById.put(sceneId, scene);
        }

        if (requiredSceneIds.size() != scenes.size()
                || !existingById.keySet().equals(requestedIds)) {
            throw new IllegalArgumentException(
                    "sceneIds must contain every scene in this project exactly once"
            );
        }

        List<StoryboardScene> reordered = requiredSceneIds.stream()
                .map(existingById::get)
                .toList();

        scenes.clear();
        scenes.addAll(reordered);
    }

    public List<StoryboardScene> getScenes() {
        return Collections.unmodifiableList(scenes);
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "title is too long (max " + TITLE_MAX_LENGTH + ")"
            );
        }
        return trimmed;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
