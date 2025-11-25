//package com.onfilm.domain.movie.repository;
//
//import com.onfilm.domain.movie.entity.MoviePerson;
//import com.querydsl.jpa.impl.JPAQueryFactory;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//
//import static toyproject.onfilm.movie.entity.QMovie.movie;
//import static toyproject.onfilm.movieperson.entity.QMoviePerson.*;
//
//
//@Repository
//@RequiredArgsConstructor
//public class MoviePersonQueryRepositoryImpl implements MoviePersonQueryRepository {
//
//    private final JPAQueryFactory query;
//
//    @Override
//    public List<MoviePerson> findFilmography(Long personId) {
//        return query
//                .selectFrom(moviePerson)
//                .join(moviePerson.movie, movie).fetchJoin()
//                .where(moviePerson.person.id.eq(personId))
//                .fetch();
//    }
//}