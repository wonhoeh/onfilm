package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class ProfileTagPersistenceTest {

    @Autowired
    private PersonRepository personRepository;

    @Test
    void 기존_프로필_태그_뒤에_신규_태그를_저장한다() {
        Person person = Person.create(
                "테스트 배우",
                null,
                null,
                null,
                null,
                List.of(),
                List.of("연기", "독립영화", "단편영화")
        );
        personRepository.saveAndFlush(person);

        person.addProfileTag("신규 태그");
        personRepository.flush();

        assertThat(person.getProfileTags())
                .extracting(tag -> tag.getRawText())
                .containsExactly("연기", "독립영화", "단편영화", "신규 태그");
        assertThat(person.getProfileTags().get(3).getId()).isNotNull();
    }
}
