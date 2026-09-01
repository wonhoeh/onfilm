package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.StoryboardCard;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.movie.entity.StoryboardScene;
import com.onfilm.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersonRepositoryMySqlIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void storyboardOrderAndOrphanRemovalArePersistedByMySql() {
        Person person = createPerson();
        StoryboardProject project = person.addStoryboardProject("프로젝트");
        StoryboardScene first = project.addScene("씬 1", "<p>첫 장면</p>");
        StoryboardScene removed = project.addScene("씬 2", "<p>삭제할 장면</p>");
        StoryboardScene third = project.addScene("씬 3", "<p>세 번째 장면</p>");
        StoryboardCard removedSceneCard = removed.addCard("storyboard/removed/card.jpg");
        StoryboardCard firstCard = first.addCard("storyboard/first/card.jpg");
        StoryboardCard secondCard = first.addCard(null);
        StoryboardCard thirdCard = first.addCard("storyboard/third/card.jpg");
        personRepository.saveAndFlush(person);

        Long personId = person.getId();
        String publicId = person.getPublicId();
        Long projectId = project.getId();
        Long removedSceneId = removed.getId();
        Long removedSceneCardId = removedSceneCard.getId();
        Long removedFirstCardId = firstCard.getId();

        project.reorderScenes(List.of(third.getId(), first.getId(), removed.getId()));
        project.removeScene(removed);
        first.replaceCards(List.of(
                new StoryboardScene.CardChange(thirdCard.getId(), thirdCard.getImageKey()),
                new StoryboardScene.CardChange(secondCard.getId(), null)
        ));
        personRepository.flush();
        entityManager.clear();

        Person reloaded = personRepository.findByPublicIdWithStoryboards(publicId)
                .orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(personId);
        assertThat(reloaded.getStoryboardProjects())
                .extracting(StoryboardProject::getId)
                .containsExactly(projectId);

        StoryboardProject reloadedProject = reloaded.getStoryboardProjects().get(0);
        assertThat(reloadedProject.getScenes())
                .extracting(StoryboardScene::getTitle)
                .containsExactly("씬 3", "씬 1");
        assertThat(storedSceneSortOrders(projectId)).containsExactly(0, 1);

        StoryboardScene reloadedFirst = reloadedProject.getScenes().get(1);
        assertThat(reloadedFirst.getCards())
                .extracting(StoryboardCard::getImageKey)
                .containsExactly("storyboard/third/card.jpg", null);
        assertThat(storedCardSortOrders(reloadedFirst.getId())).containsExactly(0, 1);

        assertThat(entityManager.find(StoryboardScene.class, removedSceneId)).isNull();
        assertThat(entityManager.find(StoryboardCard.class, removedSceneCardId)).isNull();
        assertThat(entityManager.find(StoryboardCard.class, removedFirstCardId)).isNull();
    }

    private List<Integer> storedSceneSortOrders(Long projectId) {
        return integerColumn("""
                select sort_order
                from storyboard_scene
                where project_id = :parentId
                order by sort_order
                """, projectId);
    }

    private List<Integer> storedCardSortOrders(Long sceneId) {
        return integerColumn("""
                select sort_order
                from storyboard_card
                where scene_id = :parentId
                order by sort_order
                """, sceneId);
    }

    private List<Integer> integerColumn(String sql, Long parentId) {
        List<?> values = entityManager.createNativeQuery(sql)
                .setParameter("parentId", parentId)
                .getResultList();
        return values.stream()
                .map(value -> ((Number) value).intValue())
                .toList();
    }

    private static Person createPerson() {
        return Person.create(
                "스토리보드 작성자",
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
