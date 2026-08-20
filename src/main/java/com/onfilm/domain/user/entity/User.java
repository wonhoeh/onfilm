package com.onfilm.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.onfilm.domain.movie.entity.Person;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(
                        name = "uk_users_username_normalized",
                        columnNames = "username_normalized"
                )
        }
)
public class User {
    public static final int ENCODED_PASSWORD_MAX_LENGTH = 255;
    public static final int AVATAR_IMAGE_KEY_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(nullable = false, length = UserEmail.MAX_LENGTH)
    private String email;

    @JsonIgnore
    @Column(name = "encoded_password", nullable = false, length = ENCODED_PASSWORD_MAX_LENGTH)
    private String encodedPassword;

    @Column(nullable = false, length = Username.MAX_LENGTH)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = Username.MAX_LENGTH)
    private String usernameNormalized;

    @Column(name = "avatar_image_key", length = AVATAR_IMAGE_KEY_MAX_LENGTH)
    private String avatarImageKey;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    private User(UserEmail email, String encodedPassword, Username username) {
        UserEmail requiredEmail = require(email, "email");
        Username requiredUsername = require(username, "username");
        this.email = requiredEmail.value();
        this.encodedPassword = requireEncodedPassword(encodedPassword);
        this.username = requiredUsername.value();
        this.usernameNormalized = requiredUsername.normalized();
    }

    public static User create(
            UserEmail email,
            String encodedPassword,
            Username username
    ) {
        return new User(email, encodedPassword, username);
    }

    public Person createPerson(String name) {
        return createPerson(name, null, null, null, null, List.of(), List.of());
    }

    public Person createPerson(
            String name,
            LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageKey,
            List<Person.SnsRegistration> snsList,
            List<String> rawTags
    ) {
        if (this.person != null) {
            throw new IllegalStateException("user already has a person");
        }

        Person created = Person.create(
                name,
                birthDate,
                birthPlace,
                oneLineIntro,
                profileImageKey,
                snsList,
                rawTags
        );
        attachPerson(created);
        return created;
    }

    public void attachPerson(Person person) {
        Person requiredPerson = require(person, "person");
        if (this.person != null && this.person != requiredPerson) {
            throw new IllegalStateException("user already has another person");
        }
        if (requiredPerson.getUser() != null && requiredPerson.getUser() != this) {
            throw new IllegalStateException("person already belongs to another user");
        }

        this.person = requiredPerson;
        if (requiredPerson.getUser() != this) {
            requiredPerson.attachUser(this);
        }
    }

    public void changeEncodedPassword(String encodedPassword) {
        this.encodedPassword = requireEncodedPassword(encodedPassword);
    }

    public void changeAvatarImageKey(String avatarImageKey) {
        this.avatarImageKey = normalizeAvatarImageKey(avatarImageKey);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public String getEncodedPassword() {
        return encodedPassword;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public String getAvatarImageKey() {
        return avatarImageKey;
    }

    public Person getPerson() {
        return person;
    }

    private static String requireEncodedPassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("encodedPassword is required");
        }

        String value = encodedPassword.trim();
        if (value.length() > ENCODED_PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "encodedPassword is too long (max "
                            + ENCODED_PASSWORD_MAX_LENGTH + ")"
            );
        }
        return value;
    }

    private static String normalizeAvatarImageKey(String avatarImageKey) {
        if (avatarImageKey == null || avatarImageKey.isBlank()) {
            return null;
        }

        String value = avatarImageKey.trim();
        if (value.length() > AVATAR_IMAGE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "avatarImageKey is too long (max "
                            + AVATAR_IMAGE_KEY_MAX_LENGTH + ")"
            );
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            throw new IllegalArgumentException("avatarImageKey must be a storage key");
        }
        if (value.startsWith("/") || value.contains("\\")) {
            throw new IllegalArgumentException("invalid avatarImageKey");
        }
        return value;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
