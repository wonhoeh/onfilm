package com.onfilm.domain.movie.entity;

import com.onfilm.domain.movie.dto.MoviePersonRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
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

    // 배우일 때만 사용하는 필드 (감독/작가는 null)
    @Column(name = "characterName", nullable = true)
    private String characterName;

    private MoviePerson(Person person, PersonRole role, String characterName) {
        this.person = person;
        this.role = role;
        this.characterName = characterName;
    }

    public static MoviePerson create(Movie movie, Person person, MoviePersonRequest request) {
        MoviePerson moviePerson = new MoviePerson(person, request.getRole(), request.getCharacterName());
        movie.addMoviePerson(moviePerson); // 연관관계 양방향 설정: movie <-> moviePerson

        return moviePerson;
    }

    void setMovie(Movie movie) {
        this.movie = movie;
    }
}
