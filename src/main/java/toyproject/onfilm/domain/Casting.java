package toyproject.onfilm.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Movie - Casting - Actor
 * Movie: 영화에 대한 정보를 가지고 있음
 * Casting: 영화와 배우 간의 관계를 나타냄, 각 영화에 출연한 배우(및 그 배역)에 대한 정보를 포함하고 있음
 * Actor: 배우에 대한 정보를 가지고 있음
 *
 * N:N의 관계... MovieActor 엔티티로 다대다 관계를 풀어줘야함
 * Movie는 영화 자체의 내용들
 * MovieActor는 작품의 내용들 (출연한 배우, 감독, 영화의 정보)
 * MovieActor에는 N명의 배우가 있고 N명의 감독이 있음
 *
 */

@Entity
@Data
public class Casting {
    @Id @GeneratedValue
    @Column(name = "casting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Actor actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "director_id")
    private Director director;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenarist_id")
    private Scenarist scenarist;

    private String actorsRole;    // 배우의 배역 정보 (롸다주 -> 토니 스타크)
    //배역 정보



    //=== 연관 관계 메서드 ===
    public void setMovie(Movie movie) {
        this.movie = movie;
        movie.setCasting(this);
    }

    public void setActor(Actor actor) {
        this.actor = actor;
        actor.getFilmography().add(this);
    }

    public void setDirector(Director director) {
        this.director = director;
        director.getFilmography().add(this);
    }

    public void setScenarist(Scenarist scenarist) {
        this.scenarist = scenarist;
        scenarist.getFilmography().add(this);
    }

    //=== 기본 생성자 ===
    public Casting() {}

    //=== 생성 메서드 ===
    public static Casting createCasting(Movie movie, Actor actor, Director director, Scenarist scenarist, String actorsRole) {
        Casting casting = new Casting();
        casting.setMovie(movie);
        casting.setActor(actor);
        casting.setDirector(director);
        casting.setScenarist(scenarist);
        casting.actorsRole = actorsRole;

        return casting;
    }
}
