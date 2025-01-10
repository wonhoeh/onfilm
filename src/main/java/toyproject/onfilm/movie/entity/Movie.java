package toyproject.onfilm.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.comment.entity.Comment;
import toyproject.onfilm.like.entity.Like;
import toyproject.onfilm.movieactor.entity.MovieActor;
import toyproject.onfilm.moviedirector.entity.MovieDirector;
import toyproject.onfilm.moviegenre.dto.MovieGenre;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.moviewriter.entity.MovieWriter;

import java.time.LocalDateTime;
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
 * - 엔티티
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Movie {

    //감독, 출연, 각본, 장르, 영화 특징, 관람등급
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long id;

    //제목
    private String title;
    //런타임
    private int runtime;
    //관람 등급
    private String ageRating;
    //상영일
    private LocalDateTime releaseDate;

//    //종료일
//    private LocalDate closeDate;

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

    //예고편, 섬네일
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    private List<MovieTrailer> movieTrailers = new ArrayList<>();

    /**
     * 하나의 영화는 하나의 관람등급이 있다
     * 하나의 관람등급은 여러 개의 영화에 포함된다
     * 영화:관람등급 = N:1
     */

    //=== 연관 관계 메서드 ===//
    public void setActor(MovieActor movieActor) {
        movieActors.add(movieActor);
        movieActor.setMovie(this);
    }

    public void setTrailer(MovieTrailer movieTrailer) {
        movieTrailers.add(movieTrailer);
        movieTrailer.setMovie(this);
    }

    public void setMovieInfo(String title, int runtime, String ageRating, LocalDateTime releaseDate) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
    }

    //=== 생성 메서드 ===//
    public static Movie createMovie(String title, int runtime, String rating, LocalDateTime releaseDate) {
        Movie movie = new Movie();
        movie.setMovieInfo(title, runtime, rating, releaseDate);
        return movie;
    }
}
