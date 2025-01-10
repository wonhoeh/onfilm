//package toyproject.onfilm.domain.movieactor;
//
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.annotation.Rollback;
//import org.springframework.transaction.annotation.Transactional;
//import toyproject.onfilm.domain.BaseProfileEntity;
//import toyproject.onfilm.domain.actor.Actor;
//import toyproject.onfilm.domain.actor.ActorRepository;
//import toyproject.onfilm.domain.movie.Movie;
//import toyproject.onfilm.domain.movie.MovieRepository;
//
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.*;
//
//@SpringBootTest
//public class MovieActorTest {
//
//    @Autowired MovieActorRepository movieActorRepository;
//    @Autowired ActorRepository actorRepository;
//    @Autowired MovieRepository movieRepository;
//
//    @Test
//    @Transactional
//    void addMovie() {
//        //given
//        //=== Actor 생성 ===
//        String name = "토니 스타크";
//        int age = 40;
//        int height = 180;
//        int weight = 70;
//        String sns = "www.instagram.com/hello";
//
//
//        Actor actor = actorRepository.save(Actor.builder()
//                .name(name)
//                .age(age)
//                .height(height)
//                .weight(weight)
//                .sns(sns)
//                .filmography(new ArrayList<>())
//                .build());
//
//
//        //=== Movie 생성 ===
//        String title = "Avengers";
//        List<String> genres = new ArrayList<>();
//        genres.add("action");
//        genres.add("comic");
//        LocalDate releaseDate = LocalDate.of(2020, 1, 24);
//        LocalDate closeDate = LocalDate.of(2020, 2, 24);
//
//        Movie movie = movieRepository.save(Movie.builder()
//                .title(title)
//                .genres(genres)
//                .releaseDate(releaseDate)
//                .closeDate(closeDate)
//                .movieActors(new ArrayList<>())
//                .movieDirectors(new ArrayList<>())
//                .movieWriters(new ArrayList<>())
//                .build());
//
//
//        //===MovieActor 생성 ===
//        String actorRole = "Iron Man";
//        MovieActor movieActor = movieActorRepository.save(MovieActor.createCasting(movie, actor, actorRole));
//
//        //when
//        MovieActor findMovieActor = movieActorRepository.findAll().get(0);
//
//        //then
//        assertThat(findMovieActor.getMovie().getTitle()).isEqualTo(movieActor.getMovie().getTitle());
//        assertThat(findMovieActor.getActor().getName()).isEqualTo(movieActor.getActor().getName());
//        assertThat(findMovieActor.getActorsRole()).isEqualTo(movieActor.getActorsRole());
//    }
//}
