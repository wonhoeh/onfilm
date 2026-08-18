package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProfileTagDataSqlPersistenceTest {

    @Autowired
    private PersonRepository personRepository;

    @Test
    void dataSql이_실행된_후에도_신규_프로필_태그를_저장할_수_있다() {
        Person person = personRepository.findById(1L).orElseThrow();

        person.addProfileTag("신규 태그");
        personRepository.flush();

        assertThat(person.getProfileTags())
                .extracting(tag -> tag.getRawText())
                .containsExactly("연기", "독립영화", "단편영화", "신규 태그");
        assertThat(person.getProfileTags().get(3).getId()).isNotNull();
    }
}
