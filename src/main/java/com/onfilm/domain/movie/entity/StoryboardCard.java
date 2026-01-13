package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryboardCard {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 512)
    private String imageKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private StoryboardScene scene;

    public StoryboardCard(String imageKey) {
        this.imageKey = imageKey;
    }

    public void attachScene(StoryboardScene scene) {
        this.scene = scene;
    }

    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }
}
