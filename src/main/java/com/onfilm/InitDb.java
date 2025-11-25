package com.onfilm;//package toyproject.onfilm;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.persistence.EntityManager;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import toyproject.onfilm.actor.entity.Actor;
//import toyproject.onfilm.comment.entity.Comment;
//import toyproject.onfilm.common.Profile;
//import toyproject.onfilm.director.entity.Director;
//import toyproject.onfilm.genre.entity.Genre;
//import toyproject.onfilm.like.entity.MovieLike;
//import toyproject.onfilm.movie.entity.Movie;
//import toyproject.onfilm.movieactor.entity.MovieActor;
//import toyproject.onfilm.moviedirector.entity.MovieDirector;
//import toyproject.onfilm.movietrailer.entity.MovieTrailer;
//import toyproject.onfilm.moviewriter.entity.MovieWriter;
//import toyproject.onfilm.writer.entity.Writer;
//
//import java.time.LocalDateTime;
//import java.time.Month;
//import java.util.UUID;
//
//@Component
//@RequiredArgsConstructor
//public class InitDb {
//
//    private final InitService initService;
//
//    @PostConstruct
//    public void init() {
//        initService.dbInit0();
//        initService.dbInit1();
//        initService.dbInit2();
//    }
//
//    @Slf4j
//    @Component
//    @Transactional
//    @RequiredArgsConstructor
//    static class InitService {
//
//        private final EntityManager em;
//        private final MongoTemplate mongoTemplate;
//
//        public void dbInit0() {
//            mongoTemplate.dropCollection("genres");
//            mongoTemplate.dropCollection("likes");
//            mongoTemplate.dropCollection("comments");
//            mongoTemplate.save(new Genre("드라마"));
//            mongoTemplate.save(new Genre("스릴러"));
//        }
//
//        public void dbInit1() {
//            //=== 감독의 인적 사항 ===//
//            Director director = createDirector("추창민", 50, "instagram/ccm_m");
//            em.persist(director);
//
//            //=== 작가의 인적 사항 ===//
//            Writer writer1 = createWriter("황조윤", null, null);
//            Writer writer2 = createWriter("안소정", null, null);
//            em.persist(writer1);
//            em.persist(writer2);
//
//            //=== 배우의 인적 사항 ===//
//            Actor actor1 = createActor("이병헌", 50, "instagram/lbhZzz");
//            Actor actor2 = createActor("이병헌", 50, "instagram/lbhZzz");
//            Actor actor3 = createActor("류승룡", 50, "instagram/ysyVVV");
//            em.persist(actor1);
//            em.persist(actor2);
//            em.persist(actor3);
//
//            //=== 영화 파일 URL, 시놉시스 ===//
//            String synopsis = "조선의 왕 광해와 광해군을 대신하여 나라를 다스린 하선의 이야기입니다.";
//            String movieUrl = UUID.randomUUID() + "_" + "광해";
//
//            //=== 영화 정보 ===//
//            Movie movie = Movie.createMovie("광해", 120, "15+",
//                    LocalDateTime.of(2025, Month.JANUARY, 10, 15, 30),
//                    synopsis, movieUrl);
//
//            //=== 생성자에 movie가 필요한 것들 ===//
//
//            //=== 영화 감독 ===//
//            MovieDirector movieDirector = MovieDirector.createMovieDirector(movie, director);
//            em.persist(movieDirector);
//
//            //=== 영화 작가 ===//
//            MovieWriter movieWriter1 = MovieWriter.createMovieWriter(movie, writer1);
//            MovieWriter movieWriter2 = MovieWriter.createMovieWriter(movie, writer2);
//            em.persist(movieWriter1);
//            em.persist(movieWriter2);
//
//            //=== 출연 배우 ===//
//            MovieActor movieActor1 = MovieActor.createCasting(movie, actor1, "광해군");
//            MovieActor movieActor2 = MovieActor.createCasting(movie, actor2, "하선");
//            MovieActor movieActor3 = MovieActor.createCasting(movie, actor3, "허균");
//            em.persist(movieActor1);
//            em.persist(movieActor2);
//            em.persist(movieActor3);
//
//            //=== 감독 입력 ===//
//            movie.addDirector(movieDirector);
//
//            //=== 작가 입력 ===//
//            movie.addWriter(movieWriter1);
//            movie.addWriter(movieWriter2);
//
//            //=== 출연 배우 입력 ===//
//            movie.addActor(movieActor1);
//            movie.addActor(movieActor2);
//            movie.addActor(movieActor3);
//
//            //=== 섬네일, 트레일러 입력 ===//
//            String trailerUrl = "https://example.com/1.mp4";
//            String thumbnailUrl = "https://example.com/1.jpg";
//            movie.addTrailer(new MovieTrailer(trailerUrl, thumbnailUrl));
//
//            //=== 장르 입력 ===//
//            Query query = new Query();
//            query.addCriteria(Criteria.where("name").is("드라마"));
//            Genre genre = mongoTemplate.findOne(query, Genre.class);
//            movie.addGenre(genre.getId());
//
//            //=== 댓글 입력 ===//
//            Comment comment1 = new Comment(movie.getId(), "관람객1", "잘봤습니다!");
//            Comment comment2 = new Comment(movie.getId(), "관람객2", "재밌어요!!!");
//            mongoTemplate.save(comment1);
//            mongoTemplate.save(comment2);
//            movie.addComment(comment1.getId());
//            movie.addComment(comment2.getId());
//
//            //=== 좋아요 입력 ===//
//            MovieLike like1 = MovieLike.create(movie.getId(), "ABC123");
//            MovieLike like2 = MovieLike.create(movie.getId(), "DEF456");
//            mongoTemplate.save(like1);
//            mongoTemplate.save(like2);
//            movie.addLike(like1.getId());
//            movie.addLike(like2.getId());
//
//            em.persist(movie);
//        }
//
//        public void dbInit2() {
//            //=== 감독 등록 ===//
//            Director director = createDirector("허중회", 37, "instagram/hjh_h");
//            em.persist(director);
//
//            //=== 작가 등록 ===//
//            Writer writer1 = createWriter("허중회", 37, "instagram/hjh_hh");
//            Writer writer2 = createWriter("손정아", 34, "instagram/sjAahhh");
//            em.persist(writer1);
//            em.persist(writer2);
//
//            //=== 배우 등록 ===//
//            Actor actor1 = createActor("허중회", 37, "instagram/hjh_h");
//            Actor actor2 = createActor("손정아", 34, "instagram/sjAahhh");
//            em.persist(actor1);
//            em.persist(actor2);
//
//            String movieFileUrl = "https://onfilm-static-files.s3.ap-northeast-2.amazonaws.com/movies/5e3086d2-c2bd-4d25-b738-ed50da268b77.mp4";
//            String synopsis = "시놉시스입니다. 여기에 간단하게 영화 소개글이 작성될 예정입니다.";
//
//            Movie movie = Movie.createMovie("얼린미역국", 35, "15+",
//                    LocalDateTime.of(2025, Month.JANUARY, 30, 15, 30),
//                    synopsis, movieFileUrl);
//
//            MovieDirector movieDirector = MovieDirector.createMovieDirector(movie, director);
//            em.persist(movieDirector);
//
//            MovieWriter movieWriter1 = MovieWriter.createMovieWriter(movie, writer1);
//            MovieWriter movieWriter2 = MovieWriter.createMovieWriter(movie, writer2);
//            em.persist(movieWriter1);
//            em.persist(movieWriter2);
//
//            MovieActor movieActor1 = MovieActor.createCasting(movie, actor1, "팀장");
//            MovieActor movieActor2 = MovieActor.createCasting(movie, actor2, "대리");
//            em.persist(movieActor1);
//            em.persist(movieActor2);
//
//            movie.addDirector(movieDirector);
//
//            movie.addWriter(movieWriter1);
//            movie.addWriter(movieWriter2);
//
//            movie.addActor(movieActor1);
//            movie.addActor(movieActor2);
//
//            String trailerUrl = "https://example.com/2.mp4";
//            String thumbnailUrl = "https://example.com/2.jpg";
//
//            movie.addTrailer(new MovieTrailer(trailerUrl, thumbnailUrl));
//
//            em.persist(movie);
//        }
//
//
//        private static Director createDirector(String name, Integer age, String sns) {
//            Profile profile = Profile.builder()
//                    .name(name)
//                    .age(age)
//                    .sns(sns)
//                    .build();
//
//            return Director.builder()
//                    .profile(profile)
//                    .build();
//        }
//
//        private static Writer createWriter(String name, Integer age, String sns) {
//            Profile profile = Profile.builder()
//                    .name(name)
//                    .age(age)
//                    .sns(sns)
//                    .build();
//
//            return Writer.builder()
//                    .profile(profile)
//                    .build();
//        }
//
//        private static Actor createActor(String name, Integer age, String sns) {
//            Profile profile = Profile.builder()
//                    .name(name)
//                    .age(age)
//                    .sns(sns)
//                    .build();
//
//            return Actor.builder()
//                    .profile(profile)
//                    .build();
//        }
//   }
//}
