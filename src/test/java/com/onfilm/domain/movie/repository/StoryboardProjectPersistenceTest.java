package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StoryboardProjectPersistenceTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 씬_순서를_저장하고_삭제된_씬을_orphanRemoval로_제거한다() {
        Person person = personRepository.findById(1L).orElseThrow();
        StoryboardProject project = person.getStoryboardProjects().stream()
                .filter(candidate -> candidate.getId().equals(1L))
                .findFirst()
                .orElseThrow();
        StoryboardScene first = project.getScenes().get(0);
        StoryboardScene second = project.getScenes().get(1);
        StoryboardScene third = project.getScenes().get(2);

        Long personId = person.getId();
        Long projectId = project.getId();
        Long removedSceneId = second.getId();

        project.reorderScenes(List.of(third.getId(), first.getId(), second.getId()));
        project.removeScene(second);
        personRepository.flush();
        entityManager.clear();

        Person found = personRepository.findById(personId).orElseThrow();
        StoryboardProject foundProject = found.getStoryboardProjects().stream()
                .filter(candidate -> candidate.getId().equals(projectId))
                .findFirst()
                .orElseThrow();

        assertThat(foundProject.getScenes())
                .extracting(StoryboardScene::getTitle)
                .containsExactly("씬 3", "씬 1");
        assertThat(entityManager.find(StoryboardScene.class, removedSceneId)).isNull();
    }

    @Test
    void 카드_순서를_저장하고_삭제된_카드를_orphanRemoval로_제거한다() {
        Person person = personRepository.findById(1L).orElseThrow();
        StoryboardScene scene = person.getStoryboardProjects().get(0).getScenes().get(0);
        StoryboardCard first = scene.addCard("first.jpg");
        StoryboardCard second = scene.addCard(null);
        StoryboardCard third = scene.addCard("third.jpg");
        personRepository.flush();

        Long sceneId = scene.getId();
        Long removedCardId = first.getId();
        scene.replaceCards(List.of(
                new StoryboardScene.CardChange(third.getId(), "third.jpg"),
                new StoryboardScene.CardChange(second.getId(), null)
        ));
        personRepository.flush();
        entityManager.clear();

        StoryboardScene foundScene = entityManager.find(StoryboardScene.class, sceneId);
        assertThat(foundScene.getCards()).extracting(StoryboardCard::getImageKey)
                .containsExactly("third.jpg", null);
        assertThat(entityManager.find(StoryboardCard.class, removedCardId)).isNull();
    }
}
