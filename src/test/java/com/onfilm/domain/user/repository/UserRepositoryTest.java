package com.onfilm.domain.user.repository;

import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void normalizedQueries_findCanonicalEmailAndCaseInsensitiveUsername() {
        User saved = userRepository.saveAndFlush(
                createUser("User@Example.com", "TestUser")
        );

        assertThat(userRepository.findByEmail("user@example.com"))
                .contains(saved);
        assertThat(userRepository.findByUsernameNormalized("testuser"))
                .contains(saved);
    }

    @Test
    void uniqueConstraint_rejectsCanonicalEmailDuplicate() {
        userRepository.saveAndFlush(createUser("user@example.com", "TestUser"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                createUser("USER@example.com", "another-user")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraint_rejectsCaseInsensitiveUsernameDuplicate() {
        userRepository.saveAndFlush(createUser("other@example.com", "OtherUser"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                createUser("third@example.com", "otheruser")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUser_cascadesToOwnedPerson() {
        User user = userRepository.saveAndFlush(createUser("user@example.com", "testuser"));
        Long personId = user.getPerson().getId();

        userRepository.delete(user);
        userRepository.flush();

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
}
