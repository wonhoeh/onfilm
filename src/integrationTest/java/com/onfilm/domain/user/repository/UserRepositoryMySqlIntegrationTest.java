package com.onfilm.domain.user.repository;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryMySqlIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void canonicalValuesArePersistedAndQueried() {
        User saved = userRepository.saveAndFlush(
                createUser("User@Example.com", "TestUser")
        );

        assertThat(userRepository.findByEmail("user@example.com")).contains(saved);
        assertThat(userRepository.findByUsernameNormalized("testuser")).contains(saved);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getUsernameNormalized()).isEqualTo("testuser");
    }

    @Test
    void uniqueConstraintRejectsCanonicalEmailDuplicate() {
        userRepository.saveAndFlush(createUser("user@example.com", "first-user"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                createUser("USER@example.com", "second-user")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintRejectsCaseInsensitiveUsernameDuplicate() {
        userRepository.saveAndFlush(createUser("first@example.com", "SameUser"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                createUser("second@example.com", "sameuser")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notNullConstraintRejectsMissingEmail() {
        Person person = personRepository.saveAndFlush(createPerson("이메일 누락"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into users (
                            person_id, email, encoded_password,
                            username, username_normalized, avatar_image_key
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                person.getId(), null, "encoded-password",
                "missing-email", "missing-email", null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyConstraintRejectsUnknownPerson() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into users (
                            person_id, email, encoded_password,
                            username, username_normalized, avatar_image_key
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                Long.MAX_VALUE, "orphan@example.com", "encoded-password",
                "orphan-user", "orphan-user", null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserAlsoDeletesOwnedPerson() {
        User user = userRepository.saveAndFlush(
                createUser("delete@example.com", "delete-user")
        );
        Long userId = user.getId();
        Long personId = user.getPerson().getId();

        userRepository.deleteById(userId);
        userRepository.flush();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(personRepository.findById(personId)).isEmpty();
    }

    private static User createUser(String email, String username) {
        User user = User.create(
                UserEmail.from(email),
                "encoded-password",
                Username.from(username)
        );
        user.createPerson(username);
        return user;
    }

    private static Person createPerson(String name) {
        return Person.create(name, null, null, null, null, null, null);
    }
}
