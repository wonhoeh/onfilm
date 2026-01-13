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
    @JoinColumn(name = "person_id")
    private Person person;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    private List<StoryboardCard> cards = new ArrayList<>();

    public StoryboardScene(String title, String scriptHtml) {
        this.title = title;
        this.scriptHtml = scriptHtml;
    }

    public void attachPerson(Person person) {
        this.person = person;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateScriptHtml(String scriptHtml) {
        this.scriptHtml = scriptHtml;
    }
}
