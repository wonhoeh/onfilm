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
public class StoryboardProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120, nullable = false)
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

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("title is too long (max 120)");
        }
        return trimmed;
    }
}
