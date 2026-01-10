package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private PersonRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "castType", nullable = false)
    private CastType castType;

    // 배우일 때만 사용하는 필드 (감독/작가는 null)
    @Column(name = "characterName", nullable = true)
    private String characterName;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private MoviePerson(
            Person person,
            PersonRole role,
            CastType castType,
            String characterName) {

        this.person = person;
        this.role = role;
        this.castType = castType;
        this.characterName = characterName;
    }

    public static MoviePerson create(
            Movie movie,
            Person person,
            PersonRole role,
            CastType castType,
            String characterName) {

        if (movie == null) {
            throw new IllegalArgumentException("movie is required");
        }
        if (person == null) {
            throw new IllegalArgumentException("person is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (castType == null) {
            throw new IllegalArgumentException("castType is required");
        }

        String cn = (characterName == null) ? null : characterName.trim();
        if (cn != null && cn.isBlank()) cn = null;

        MoviePerson moviePerson = MoviePerson.builder()
                .person(person)
                .role(role)
                .castType(castType)
                .characterName(characterName)
                .build();
        movie.addMoviePerson(moviePerson);

        return moviePerson;
    }

    void attachMovie(Movie movie) {
        this.movie = movie;
    }

    public void updateRole(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        if (role == null || castType == null) {
            throw new IllegalArgumentException("role and castType are required");
        }
        String cn = (characterName == null) ? null : characterName.trim();
        if (cn != null && cn.isBlank()) cn = null;

        this.role = role;
        this.castType = castType;
        this.characterName = cn;
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
