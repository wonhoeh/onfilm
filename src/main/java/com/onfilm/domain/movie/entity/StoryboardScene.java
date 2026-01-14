package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryboardScene {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String title;

    @Lob
    @Column
    private String scriptHtml;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private StoryboardProject project;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    private List<StoryboardCard> cards = new ArrayList<>();

    public StoryboardScene(String title, String scriptHtml) {
        this.title = title;
        this.scriptHtml = scriptHtml;
    }

    public void attachProject(StoryboardProject project) {
        this.project = project;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateScriptHtml(String scriptHtml) {
        this.scriptHtml = scriptHtml;
    }
}
