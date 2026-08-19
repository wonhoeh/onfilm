package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryboardCard {

    public static final int IMAGE_KEY_MAX_LENGTH = 512;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = IMAGE_KEY_MAX_LENGTH)
    private String imageKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private StoryboardScene scene;

    private StoryboardCard(String imageKey) {
        this.imageKey = normalizeImageKey(imageKey);
    }

    static StoryboardCard create(String imageKey) {
        return new StoryboardCard(imageKey);
    }

    void attachScene(StoryboardScene scene) {
        StoryboardScene requiredScene = require(scene, "scene");
        if (this.scene != null && this.scene != requiredScene) {
            throw new IllegalStateException(
                    "storyboardCard already belongs to another scene"
            );
        }
        this.scene = requiredScene;
    }

    void detachScene(StoryboardScene scene) {
        if (this.scene == scene) {
            this.scene = null;
        }
    }

    public void changeImageKey(String imageKey) {
        this.imageKey = normalizeImageKey(imageKey);
    }

    static String normalizeImageKey(String imageKey) {
        if (imageKey == null) {
            return null;
        }

        String trimmed = imageKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > IMAGE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "imageKey is too long (max " + IMAGE_KEY_MAX_LENGTH + ")"
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
