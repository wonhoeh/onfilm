package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "movie_person_role",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_movie_person_role_participation_role",
                columnNames = {"movie_person_id", "role"}
        )
)
@Check(
        name = "ck_movie_person_role_actor_fields",
        constraints = "(role = 'ACTOR' and cast_type is not null) " +
                "or (role in ('DIRECTOR', 'WRITER') and cast_type is null and character_name is null)"
)
public class MoviePersonRole {
    public static final int ROLE_MAX_LENGTH = 20;
    public static final int CAST_TYPE_MAX_LENGTH = 20;
    public static final int CHARACTER_NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_person_id", nullable = false)
    private MoviePerson moviePerson;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = ROLE_MAX_LENGTH)
    private PersonRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "cast_type", length = CAST_TYPE_MAX_LENGTH)
    private CastType castType;

    @Column(name = "character_name", length = CHARACTER_NAME_MAX_LENGTH)
    private String characterName;

    private MoviePersonRole(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        this.role = require(role, "role");
        applyDetails(castType, characterName);
    }

    static MoviePersonRole create(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        return new MoviePersonRole(role, castType, characterName);
    }

    void attachMoviePerson(MoviePerson moviePerson) {
        MoviePerson requiredMoviePerson = require(moviePerson, "moviePerson");
        if (this.moviePerson != null && this.moviePerson != requiredMoviePerson) {
            throw new IllegalStateException("role already belongs to another moviePerson");
        }
        this.moviePerson = requiredMoviePerson;
    }

    void detachMoviePerson(MoviePerson moviePerson) {
        if (this.moviePerson == moviePerson) {
            this.moviePerson = null;
        }
    }

    void changeDetails(CastType castType, String characterName) {
        applyDetails(castType, characterName);
    }

    private void applyDetails(CastType castType, String characterName) {
        if (role == PersonRole.ACTOR) {
            this.castType = require(castType, "castType");
            this.characterName = normalizeCharacterName(characterName);
            return;
        }

        if (castType != null || normalizeCharacterName(characterName) != null) {
            throw new IllegalArgumentException("actor details are only allowed for actor role");
        }
        this.castType = null;
        this.characterName = null;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String normalizeCharacterName(String characterName) {
        if (characterName == null) {
            return null;
        }

        String trimmed = characterName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > CHARACTER_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "characterName is too long (max " + CHARACTER_NAME_MAX_LENGTH + ")"
            );
        }
        return trimmed;
    }
}
