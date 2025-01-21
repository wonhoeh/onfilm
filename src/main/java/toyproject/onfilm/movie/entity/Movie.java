package toyproject.onfilm.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import toyproject.onfilm.movieactor.entity.MovieActor;
import toyproject.onfilm.moviedirector.entity.MovieDirector;
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

    private String title; //제목
    private int runtime;    //상영 시간
    private String ageRating; //관람 등급
    private LocalDateTime releaseDate; //개봉일
    private String synopsis;  //영화 줄거리
    private String movieFileUrl; //영화 파일 url


//    //종료일
//    private LocalDate closeDate;

    //영화에 출연한 배우들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 10)   //한 번에 10개의 MovieActor를 로드
    private List<MovieActor> movieActors = new ArrayList<>();

    //영화 제작에 참여한 감독들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieDirector> movieDirectors = new ArrayList<>();

    //시나리오 집필한 작가들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieWriter> movieWriters = new ArrayList<>();

    //영화의 장르
//    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
//    private List<MovieGenre> genres = new ArrayList<>();
    @ElementCollection
    private List<String> genreIds = new ArrayList<>();  //MongoDB Genre 컬렉션의 ID를 저장

    //영화에 달린 댓글
//    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
//    private List<Comment> comments = new ArrayList<>();
    @ElementCollection
    private List<String> comments = new ArrayList<>();

    //영화의 좋아요 수
//    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
//    private List<Like> likes = new ArrayList<>();
    @ElementCollection
    private List<String> likes = new ArrayList<>();

    //예고편, 섬네일
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    @BatchSize(size = 10) //한 번에 10개의 MovieActor를 로드
    private List<MovieTrailer> movieTrailers = new ArrayList<>();

    /**
     * 하나의 영화는 하나의 관람등급이 있다
     * 하나의 관람등급은 여러 개의 영화에 포함된다
     * 영화:관람등급 = N:1
     */

    //=== 연관 관계 메서드 ===//
    public void addDirector(MovieDirector movieDirector) {
        movieDirectors.add(movieDirector);
        movieDirector.addMovie(this);
    }

    public void addWriter(MovieWriter movieWriter) {
        movieWriters.add(movieWriter);
        movieWriter.addMovie(this);
    }

    public void addActor(MovieActor movieActor) {
        movieActors.add(movieActor);
        movieActor.addMovie(this);
    }

    public void addTrailer(MovieTrailer movieTrailer) {
        movieTrailers.add(movieTrailer);
        movieTrailer.setMovie(this);
    }

    public void addGenre(String genreId) {
        genreIds.add(genreId);
    }

    public void addComment(String commentId) {
        comments.add(commentId);
    }

    public void addLike(String movieLikeId) {
        likes.add(movieLikeId);
    }

    public void removeLike(String movieLikeId) {
        likes.remove(movieLikeId);
    }

    public void addMovieInfo(String title, int runtime, String ageRating, LocalDateTime releaseDate, String synopsis, String movieFileUrl) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.movieFileUrl = movieFileUrl;
    }

    //=== 생성 메서드 ===//
    public static Movie createMovie(String title, int runtime, String rating, LocalDateTime releaseDate, String synopsis, String movieFileUrl) {
        Movie movie = new Movie();
        movie.addMovieInfo(title, runtime, rating, releaseDate, synopsis, movieFileUrl);
        return movie;
    }
}
