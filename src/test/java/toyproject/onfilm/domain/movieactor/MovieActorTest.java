package toyproject.onfilm.domain.movieactor;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.domain.Profile;
import toyproject.onfilm.domain.actor.Actor;
import toyproject.onfilm.domain.actor.ActorRepository;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.domain.movie.MovieRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
public class MovieActorTest {

    @Autowired MovieActorRepository movieActorRepository;
    @Autowired ActorRepository actorRepository;
    @Autowired MovieRepository movieRepository;

    @Test
    @Transactional
    void addMovie() {
        //given

        //=== Actor 생성 ===
        String name = "토니 스타크";
        int age = 40;
        String phoneNumber = "010-1234-5678";
        String email = "tonyMk2@gmail.com";


        Profile profile = Profile.builder()
                .name(name)
                .age(age)
                .phoneNumber(phoneNumber)
                .email(email)
                .build();

        Actor actor = Actor.builder()
                .profile(profile)
                .build();

        actorRepository.save(actor);


        //=== Movie 생성 ===
        String title = "Avengers";
        String genre = "action";
        LocalDate releaseDate = LocalDate.of(2020, 1, 24);
        LocalDate closeDate = LocalDate.of(2020, 2, 24);

        Movie movie = Movie.builder()
                .title(title)
                .genre(genre)
                .releaseDate(releaseDate)
                .closeDate(closeDate)
                .build();

        movieRepository.save(movie);



        //===MovieActor 생성 ===
        MovieActor movieActor = movieActorRepository.save(
                MovieActor.createCasting(movie, actor, "Iron Man"));

        /**
         * TODO: ConstraintViolationException
         * movie, actor 엔티티가 영속성 상태가 아니라서 발생하는 문제같음
         * -> 엔티티를 저장할 때 연관된 엔티티도 있으면 영속성 상태여야 하는것은 맞지만
         * 해당 문제의 원인은 아니었음
         * 이전에 만들어진 테이블이 있어서 제대로 저장이 안된것
         */

        //when
        MovieActor findMovieActor = movieActorRepository.findAll().get(0);

        //then
        assertThat(findMovieActor.getMovie().getTitle()).isEqualTo(movieActor.getMovie().getTitle());
        assertThat(findMovieActor.getActor().getProfile().getName()).isEqualTo(movieActor.getActor().getProfile().getName());
        assertThat(findMovieActor.getActorsRole()).isEqualTo(movieActor.getActorsRole());
    }
}
