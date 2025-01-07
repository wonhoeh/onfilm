package toyproject.onfilm.domain.movieactor;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.actor.Actor;
import toyproject.onfilm.domain.movie.Movie;

/**
 * Movie - Casting - Actor
 * Movie: 영화에 대한 정보를 가지고 있음
 * Casting: 영화와 배우 간의 관계를 나타냄, 각 영화에 출연한 배우(및 그 배역)에 대한 정보를 포함하고 있음
 * 영화에 출연한 기록이나 배역 정보를 담고 있는 중간 엔티티
 * Actor: 배우에 대한 정보를 가지고 있음
 *
 * N:N의 관계... MovieActor 엔티티로 다대다 관계를 풀어줘야함
 * Movie 영화 자체의 내용들
 * MovieActor 작품의 내용들 (출연한 배우, 감독, 영화의 정보)
 * MovieActor에는 N명의 배우가 있고 N명의 감독이 있음
 *
 */

@Getter
@NoArgsConstructor
@Entity
public class MovieActor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movieactor_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Actor actor;

    //배우의 배역 정보 (롸다주 -> 토니 스타크)
    private String actorsRole;


//    //=== 연관관계 편의 메서드 ===
//    public void setMovie(Movie movie) {
//        this.movie = movie;
//        movie.getMovieActors().add(this);
//    }
//
//    public void setActor(Actor actor) {
//        this.actor = actor;
//        actor.getFilmography().add(this);
//    }

    //=== 양방향 설정 메서드 ===//
    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public void setActor(Actor actor) {
        this.actor = actor;
    }

    //=== 생성 메서드 ===
    public static MovieActor createCasting(Movie movie, Actor actor, String actorsRole) {
        MovieActor movieActor = new MovieActor();
        movieActor.setMovie(movie);
        movieActor.setActor(actor);
        movieActor.actorsRole = actorsRole;

        return movieActor;
    }


}
