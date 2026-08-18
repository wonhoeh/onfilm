package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.entity.ProfileTag;
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
                List.of("Action")
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

        Long originalSnsId = found.getSnsList().get(0).getId();
        Long originalTagId = found.getProfileTags().get(0).getId();
        found.replaceSns(List.of(PersonSns.create(
                SnsType.ETC,
                "https://instagram.com/onfilm"
        )));
        found.replaceProfileTags(List.of("새 태그", "ACTION"));
        found.removeStoryboardProject(found.getStoryboardProjects().get(0));
        personRepository.flush();
        entityManager.clear();

        Person replaced = personRepository.findById(personId).orElseThrow();
        assertThat(replaced.getSnsList()).singleElement().satisfies(sns -> {
            assertThat(sns.getId()).isEqualTo(originalSnsId);
            assertThat(sns.getType()).isEqualTo(SnsType.ETC);
        });
        assertThat(replaced.getProfileTags())
                .extracting(ProfileTag::getRawText)
                .containsExactly("새 태그", "ACTION");
        assertThat(replaced.getProfileTags().get(1).getId())
                .isEqualTo(originalTagId);
        assertThat(replaced.getStoryboardProjects()).isEmpty();

        replaced.replaceSns(List.of(PersonSns.create(
                SnsType.YOUTUBE,
                "https://youtube.com/onfilm"
        )));
        personRepository.flush();
        entityManager.clear();

        Person changedUrl = personRepository.findById(personId).orElseThrow();
        assertThat(changedUrl.getSnsList())
                .extracting(PersonSns::getType)
                .containsExactly(SnsType.YOUTUBE);
        assertThat(changedUrl.getSnsList().get(0).getId())
                .isNotEqualTo(originalSnsId);
    }
}
