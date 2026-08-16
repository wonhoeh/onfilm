package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(
                name="uk_movie_person",
                columnNames={"movie_id","person_id","role","cast_type","character_name"}
        ))
public class MoviePerson {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private PersonRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "cast_type")
    private CastType castType;

    // 배우일 때만 사용하는 필드 (감독/작가는 null)
    @Column(name = "character_name")
    private String characterName;

    // 해당 인물의 필모그래피 표시 순서
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 해당 인물 프로필에서 참여 작품 공개 여부
    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    private MoviePerson(
            Person person,
            PersonRole role,
            CastType castType,
            String characterName) {

        this.person = require(person, "person");
        applyRole(role, castType, characterName);
    }

    public static MoviePerson create(
            Movie movie,
            Person person,
            PersonRole role,
            CastType castType,
            String characterName) {

        Movie requiredMovie = require(movie, "movie");
        MoviePerson moviePerson = new MoviePerson(person, role, castType, characterName);
        requiredMovie.addMoviePerson(moviePerson);

        return moviePerson;
    }

    void attachMovie(Movie movie) {
        Movie requiredMovie = require(movie, "movie");
        if (this.movie != null && this.movie != requiredMovie) {
            throw new IllegalStateException("moviePerson already belongs to another movie");
        }
        this.movie = requiredMovie;
    }

    public void changeRole(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        applyRole(role, castType, characterName);
    }

    public void changeSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be zero or greater");
        }
        this.sortOrder = sortOrder;
    }

    public void changePrivacy(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    private void applyRole(PersonRole role, CastType castType, String characterName) {
        PersonRole requiredRole = require(role, "role");
        this.role = requiredRole;

        if (requiredRole == PersonRole.ACTOR) {
            this.castType = require(castType, "castType");
            this.characterName = normalizeCharacterName(characterName);
            return;
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
        return trimmed.isEmpty() ? null : trimmed;
    }
}
