package toyproject.onfilm;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.domain.Profile;
import toyproject.onfilm.domain.actor.Actor;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.domain.movieactor.MovieActor;

import java.time.LocalDateTime;
import java.time.Month;

@Component
@RequiredArgsConstructor
public class InitDb {

    private final InitService initService;

    @PostConstruct
    public void init() {
        initService.dbInit1();
        initService.dbInit2();
    }

    @Component
    @Transactional
    @RequiredArgsConstructor
    static class InitService {

        private final EntityManager em;

        public void dbInit1() {
            Actor actor1 = createActor("이병헌", 50, "instagram/lbhZzz");
            Actor actor2 = createActor("이병헌", 50, "instagram/lbhZzz");
            Actor actor3 = createActor("류승룡", 50, "instagram/ysyVVV");
            em.persist(actor1);
            em.persist(actor2);
            em.persist(actor3);


            Movie movie = Movie.createMovie("광해", 120, "15+",
                    LocalDateTime.of(2025, Month.JANUARY, 10, 15, 30));

            MovieActor movieActor1 = MovieActor.createCasting(movie, actor1, "광해군");
            MovieActor movieActor2 = MovieActor.createCasting(movie, actor2, "하선");
            MovieActor movieActor3 = MovieActor.createCasting(movie, actor3, "허균");

            em.persist(movieActor1);
            em.persist(movieActor2);
            em.persist(movieActor3);

            movie.addActor(movieActor1);
            movie.addActor(movieActor2);
            movie.addActor(movieActor3);

            em.persist(movie);
        }

        public void dbInit2() {
            Actor actor1 = createActor("허중회", 37, "instagram/hjh_h");
            Actor actor2 = createActor("손정아", 34, "instagram/sjAahhh");
            em.persist(actor1);
            em.persist(actor2);


            Movie movie = Movie.createMovie("얼린미역국", 120, "15+",
                    LocalDateTime.of(2025, Month.JANUARY, 30, 15, 30));

            MovieActor movieActor1 = MovieActor.createCasting(movie, actor1, "과장");
            MovieActor movieActor2 = MovieActor.createCasting(movie, actor2, "사원");

            em.persist(movieActor1);
            em.persist(movieActor2);

            movie.addActor(movieActor1);
            movie.addActor(movieActor2);

            em.persist(movie);
        }

        private static Actor createActor(String name, int age, String sns) {
            Profile profile = Profile.builder()
                    .name(name)
                    .age(age)
                    .sns(sns)
                    .build();

            Actor actor = Actor.builder()
                    .profile(profile)
                    .build();

            return actor;
        }
   }
}
