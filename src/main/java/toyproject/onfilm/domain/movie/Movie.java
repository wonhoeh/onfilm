package toyproject.onfilm.domain.movie;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.comment.Comment;
import toyproject.onfilm.domain.like.Like;
import toyproject.onfilm.domain.movieactor.MovieActor;
import toyproject.onfilm.domain.moviedirector.MovieDirector;
import toyproject.onfilm.domain.moviegenre.MovieGenre;
import toyproject.onfilm.domain.moviewriter.MovieWriter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Movie 엔티티
 * 영화 정보
 * - 제목
 * - 제작에 참여한 제작진
 * - 작품에 출연한 배우
 * - 대본을 작성한 작가
 * - 영화의 장르
 * - 개봉일
 * - 종료일
 * - 자막: LONGTEXT(42억 바이트 = 4GB)
 * - 동영상: LONGBLOB(42억 바이트 = 4GB)
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Movie {

    //감독, 출연, 각본, 장르, 영화 특징, 관람등급
    @Id @GeneratedValue
    @Column(name = "movie_id")
    private Long id;

    //영화에 출연한 배우들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieActor> movieActors = new ArrayList<>();

    //영화 제작에 참여한 감독들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieDirector> movieDirectors = new ArrayList<>();

    //시나리오 집필한 작가들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieWriter> movieWriters = new ArrayList<>();

    //영화의 장르
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<MovieGenre> genres = new ArrayList<>();

    //영화에 달린 댓글
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<Comment> comments = new ArrayList<>();

    //영화의 좋아요 수
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<Like> likes = new ArrayList<>();



    //제목
    private String title;

    //상영일
    private LocalDate releaseDate;

    //종료일
    private LocalDate closeDate;


    //=== 생성자 ===
    @Builder
    public Movie(String title, LocalDate releaseDate, LocalDate closeDate, List<MovieActor> movieActors, List<MovieDirector> movieDirectors, List<MovieWriter> movieWriters) {
        this.title = title;
        this.releaseDate = releaseDate;
        this.closeDate = closeDate;
        this.movieActors = movieActors;
        this.movieDirectors = movieDirectors;
        this.movieWriters = movieWriters;
    }
}
