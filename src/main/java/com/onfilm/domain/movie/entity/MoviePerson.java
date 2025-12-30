package com.onfilm.domain.movie.entity;

import com.onfilm.domain.movie.dto.CreateMoviePersonRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Builder(access = AccessLevel.PRIVATE)
    private MoviePerson(Movie movie,
                        Person person,
                        PersonRole role,
                        CastType castType,
                        String characterName) {

        this.movie = movie;
        this.person = person;
        this.role = role;
        this.castType = castType;
        this.characterName = characterName;
    }

    public static MoviePerson create(Movie movie,
                                     Person person,
                                     PersonRole role,
                                     CastType castType,
                                     String characterName) {
        MoviePerson moviePerson = MoviePerson.builder()
                .movie(movie)
                .person(person)
                .role(role)
                .castType(castType)
                .characterName(characterName)
                .build();

        return moviePerson;
    }

    void setMovie(Movie movie) {
        this.movie = movie;
    }
}
