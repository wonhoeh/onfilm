package com.onfilm.domain.user.entity;

import com.onfilm.domain.movie.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void create_storesCanonicalIdentityAndEncodedPassword() {
        User user = createUser("  USER@example.com  ", "TestUser");

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getUsername()).isEqualTo("TestUser");
        assertThat(user.getUsernameNormalized()).isEqualTo("testuser");
        assertThat(user.getEncodedPassword()).isEqualTo("encoded-password");
    }

    @Test
    void create_rejectsMissingOrOverlongEncodedPassword() {
        assertThatThrownBy(() -> User.create(
                UserEmail.from("user@example.com"),
                " ",
                Username.from("testuser")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("encodedPassword is required");

        assertThatThrownBy(() -> User.create(
                UserEmail.from("user@example.com"),
                "a".repeat(User.ENCODED_PASSWORD_MAX_LENGTH + 1),
                Username.from("testuser")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encodedPassword is too long");
    }

    @Test
    void createPerson_connectsBothSidesAndRejectsSecondPerson() {
        User user = createUser("user@example.com", "testuser");

        Person person = user.createPerson("테스트 배우");

        assertThat(user.getPerson()).isSameAs(person);
        assertThat(person.getUser()).isSameAs(user);
        assertThatThrownBy(() -> user.createPerson("다른 배우"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user already has a person");
    }

    @Test
    void attachPerson_rejectsPersonOwnedByAnotherUser() {
        User owner = createUser("owner@example.com", "owner-user");
        User another = createUser("another@example.com", "another-user");
        Person person = owner.createPerson("소유자");

        assertThatThrownBy(() -> another.attachPerson(person))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("person already belongs to another user");
    }

    @Test
    void changeAvatarImageKey_usesStorageKeyPolicy() {
        User user = createUser("user@example.com", "testuser");

        user.changeAvatarImageKey("  user/1/avatar.png  ");
        assertThat(user.getAvatarImageKey()).isEqualTo("user/1/avatar.png");

        user.changeAvatarImageKey(" ");
        assertThat(user.getAvatarImageKey()).isNull();

        assertThatThrownBy(() -> user.changeAvatarImageKey(
                "https://cdn.example.com/avatar.png"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("avatarImageKey must be a storage key");
    }

    @Test
    void changeEncodedPassword_validatesAndReplacesEncodedValue() {
        User user = createUser("user@example.com", "testuser");

        user.changeEncodedPassword("new-encoded-password");

        assertThat(user.getEncodedPassword()).isEqualTo("new-encoded-password");
    }

    private static User createUser(String email, String username) {
        return User.create(
                UserEmail.from(email),
                "encoded-password",
                Username.from(username)
        );
    }
}
