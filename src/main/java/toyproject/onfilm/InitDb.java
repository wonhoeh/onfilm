package toyproject.onfilm;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.common.Profile;
import toyproject.onfilm.director.entity.Director;
import toyproject.onfilm.genre.entity.Genre;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movieactor.entity.MovieActor;
import toyproject.onfilm.moviedirector.entity.MovieDirector;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.moviewriter.entity.MovieWriter;
import toyproject.onfilm.wrtier.entity.Writer;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InitDb {

    private final InitService initService;



    @PostConstruct
    public void init() {
        initService.dbInit0();
        initService.dbInit1();
        initService.dbInit2();
    }

    @Slf4j
    @Component
    @Transactional
    @RequiredArgsConstructor
    static class InitService {

        private final EntityManager em;
        private final MongoTemplate mongoTemplate;

        public void dbInit0() {
            mongoTemplate.dropCollection("genres");
            mongoTemplate.save(new Genre("드라마"));
            mongoTemplate.save(new Genre("스릴러"));
        }

        public void dbInit1() {
            Director director = createDirector("추창민", 50, "instagram/ccm_m");
            em.persist(director);

            //=== 작가 등록 ===//
            Writer writer1 = createWriter("황조윤", null, null);
            Writer writer2 = createWriter("안소정", null, null);
            em.persist(writer1);
            em.persist(writer2);

            //=== 배우 등록 ===//
            Actor actor1 = createActor("이병헌", 50, "instagram/lbhZzz");
            Actor actor2 = createActor("이병헌", 50, "instagram/lbhZzz");
            Actor actor3 = createActor("류승룡", 50, "instagram/ysyVVV");
            em.persist(actor1);
            em.persist(actor2);
            em.persist(actor3);

            //=== 영화 파일 URL, 시놉시스 작성 ===//
            String movieFileUrl = UUID.randomUUID() + "_" + "광해";
            String synopsis = "조선의 왕 광해와 광해군을 대신하여 나라를 다스린 하선의 이야기입니다.";

            //=== 영화 생성 ===//
            Movie movie = Movie.createMovie("광해", 120, "15+",
                    LocalDateTime.of(2025, Month.JANUARY, 10, 15, 30),
                    synopsis, movieFileUrl);

            //=== 생성자에 movie가 필요한 것들 ===//

            //=== 감독 등록 ===//
            MovieDirector movieDirector = MovieDirector.createMovieDirector(director, movie);
            em.persist(movieDirector);


            //=== 작가 등록 ===//
            MovieWriter movieWriter1 = MovieWriter.createMovieWriter(writer1, movie);
            MovieWriter movieWriter2 = MovieWriter.createMovieWriter(writer2, movie);
            em.persist(movieWriter1);
            em.persist(movieWriter2);

            //=== 출연 배우 등록 ===//
            MovieActor movieActor1 = MovieActor.createCasting(movie, actor1, "광해군");
            MovieActor movieActor2 = MovieActor.createCasting(movie, actor2, "하선");
            MovieActor movieActor3 = MovieActor.createCasting(movie, actor3, "허균");
            em.persist(movieActor1);
            em.persist(movieActor2);
            em.persist(movieActor3);

            movie.addDirector(movieDirector);

            movie.addWriter(movieWriter1);
            movie.addWriter(movieWriter2);

            movie.addActor(movieActor1);
            movie.addActor(movieActor2);
            movie.addActor(movieActor3);

            String trailerUrl = "https://example.com/1.mp4";
            String thumbnailUrl = "https://example.com/1.jpg";

            movie.addTrailer(new MovieTrailer(trailerUrl, thumbnailUrl));

            Query query = new Query();
            query.addCriteria(Criteria.where("name").is("드라마"));
            Genre genre = mongoTemplate.findOne(query, Genre.class);
            movie.addGenre(genre.getId());

            log.info("movie.genreId = {}", movie.getGenreIds().get(0));

            em.persist(movie);
        }

        public void dbInit2() {
            //=== 감독 등록 ===//
            Director director = createDirector("허중회", 37, "instagram/hjh_h");
            em.persist(director);

            //=== 작가 등록 ===//
            Writer writer1 = createWriter("허중회", 37, "instagram/hjh_h");
            Writer writer2 = createWriter("손정아", 34, "instagram/sjAahhh");
            em.persist(writer1);
            em.persist(writer2);

            //=== 배우 등록 ===//
            Actor actor1 = createActor("허중회", 37, "instagram/hjh_h");
            Actor actor2 = createActor("손정아", 34, "instagram/sjAahhh");
            em.persist(actor1);
            em.persist(actor2);

            String movieFileUrl = UUID.randomUUID() + "_" + "얼린미역국";
            String synopsis = "앶자돌이 동생이 촬영제작 알바하러 간 앶자돌이의 독립영화입니다.";

            Movie movie = Movie.createMovie("얼린미역국", 120, "15+",
                    LocalDateTime.of(2025, Month.JANUARY, 30, 15, 30),
                    synopsis, movieFileUrl);

            MovieDirector movieDirector = MovieDirector.createMovieDirector(director, movie);
            em.persist(movieDirector);

            MovieWriter movieWriter1 = MovieWriter.createMovieWriter(writer1, movie);
            MovieWriter movieWriter2 = MovieWriter.createMovieWriter(writer2, movie);
            em.persist(movieWriter1);
            em.persist(movieWriter2);

            MovieActor movieActor1 = MovieActor.createCasting(movie, actor1, "팀장");
            MovieActor movieActor2 = MovieActor.createCasting(movie, actor2, "대리");
            em.persist(movieActor1);
            em.persist(movieActor2);

            movie.addDirector(movieDirector);

            movie.addWriter(movieWriter1);
            movie.addWriter(movieWriter2);

            movie.addActor(movieActor1);
            movie.addActor(movieActor2);

            String trailerUrl = "https://example.com/2.mp4";
            String thumbnailUrl = "https://example.com/2.jpg";

            movie.addTrailer(new MovieTrailer(trailerUrl, thumbnailUrl));

            em.persist(movie);
        }

        private static Director createDirector(String name, Integer age, String sns) {
            Profile profile = Profile.builder()
                    .name(name)
                    .age(age)
                    .sns(sns)
                    .build();

            return Director.builder()
                    .profile(profile)
                    .build();
        }

        private static Writer createWriter(String name, Integer age, String sns) {
            Profile profile = Profile.builder()
                    .name(name)
                    .age(age)
                    .sns(sns)
                    .build();

            return Writer.builder()
                    .profile(profile)
                    .build();
        }

        private static Actor createActor(String name, Integer age, String sns) {
            Profile profile = Profile.builder()
                    .name(name)
                    .age(age)
                    .sns(sns)
                    .build();

            return Actor.builder()
                    .profile(profile)
                    .build();
        }
   }
}
