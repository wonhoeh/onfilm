package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.entity.StoryboardProject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class PersonPersistenceTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void personAggregate_persistsAndReplacesOwnedCollections() {
        Person person = Person.create(
                "테스트 배우",
                null,
                null,
                null,
                "profile/avatar.jpg",
                List.of(PersonSns.create(
                        SnsType.INSTAGRAM,
                        "https://instagram.com/onfilm"
                )),
                List.of("#독립영화")
        );
        person.addGalleryImageKey("gallery/first.jpg");
        person.addStoryboardProject(StoryboardProject.create("첫 프로젝트"));

        Person saved = personRepository.saveAndFlush(person);
        Long personId = saved.getId();
        String publicId = saved.getPublicId();
        entityManager.clear();

        Person found = personRepository.findByPublicIdWithStoryboards(publicId)
                .orElseThrow();
        assertThat(found.getPublicId()).isEqualTo(publicId);
        assertThat(found.getSnsList()).hasSize(1);
        assertThat(found.getProfileTags()).hasSize(1);
        assertThat(found.getGalleryItems()).hasSize(1);
        assertThat(found.getStoryboardProjects()).hasSize(1);
        assertThat(personRepository.findProfileImageKeyById(personId))
                .contains("profile/avatar.jpg");

        found.replaceSns(List.of(PersonSns.create(
                SnsType.YOUTUBE,
                "https://youtube.com/onfilm"
        )));
        found.removeStoryboardProject(found.getStoryboardProjects().get(0));
        personRepository.flush();
        entityManager.clear();

        Person replaced = personRepository.findById(personId).orElseThrow();
        assertThat(replaced.getSnsList())
                .extracting(PersonSns::getType)
                .containsExactly(SnsType.YOUTUBE);
        assertThat(replaced.getStoryboardProjects()).isEmpty();
    }
}
