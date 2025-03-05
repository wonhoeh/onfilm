package toyproject.onfilm.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.actor.repository.ActorRepository;
import toyproject.onfilm.exception.ActorNotFoundException;
import toyproject.onfilm.movie.dto.CreateMovieRequest;
import toyproject.onfilm.movie.dto.UpdateMovieRequest;
import toyproject.onfilm.movieactor.dto.UpdateMovieActorRequest;
import toyproject.onfilm.movieactor.entity.MovieActor;
import toyproject.onfilm.moviedirector.entity.MovieDirector;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.moviewriter.entity.MovieWriter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private String movieUrl; //영화 파일 url

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

    //예고편, 섬네일
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 10) //한 번에 10개의 MovieActor를 로드
    private List<MovieTrailer> movieTrailers = new ArrayList<>();

    //장르
    //@OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    //private List<MovieGenre> genres = new ArrayList<>();
    @ElementCollection
    private List<String> genreIds = new ArrayList<>();  //MongoDB Genre 컬렉션의 ID를 저장

    //영화에 달린 댓글
    //@OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    //private List<Comment> comments = new ArrayList<>();
    @ElementCollection
    private List<String> comments = new ArrayList<>();

    //영화의 좋아요
    //@OneToMany(mappedBy = "movie", cascade = CascadeType.ALL)
    //private List<Like> likes = new ArrayList<>();
    @ElementCollection
    private List<String> likes = new ArrayList<>();



    /**
     * 하나의 영화는 하나의 관람등급이 있다
     * 하나의 관람등급은 여러 개의 영화에 포함된다
     * 영화:관람등급 = N:1
     */

    //=== 연관 관계 메서드 ===//
    public void addActor(MovieActor movieActor) {
        movieActors.add(movieActor);
        movieActor.addMovie(this);
    }

    public void addDirector(MovieDirector movieDirector) {
        movieDirectors.add(movieDirector);
        movieDirector.addMovie(this);
    }

    public void addWriter(MovieWriter movieWriter) {
        movieWriters.add(movieWriter);
        movieWriter.addMovie(this);
    }

    public void addTrailer(MovieTrailer movieTrailer) {
        movieTrailers.add(movieTrailer);
        movieTrailer.setMovie(this);
    }

    public void addMovieUrl(String movieUrl) {
        this.movieUrl = movieUrl;
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

    public void addMovieInfo(String title, int runtime, String ageRating, LocalDateTime releaseDate, String synopsis, String movieUrl) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.movieUrl = movieUrl;
    }

    //=== 생성 메서드 ===//
    public static Movie createMovie(String title, int runtime, String rating, LocalDateTime releaseDate, String synopsis, String movieUrl) {
        Movie movie = new Movie();
        movie.addMovieInfo(title, runtime, rating, releaseDate, synopsis, movieUrl);
        return movie;
    }

    /**
     * TODO
     * - updateMovieActor, replaceMovieActor, removeMovieActor
     * - updateMovieDirector
     * - updateMovieWriter
     * - updatedMovieTrailer
     *
     * 게시글을 수정하는 경우
     * 기존의 데이터를 불러오고 수정한 내용을 저장함
     * Service 계층
     * 1. 기존의 데이터 불러오기
     * 2. 입력된 데이터 저장하기
     */

    //=== 필드 업데이트 ===//
    private void updateTitle(String title) {
        this.title = title;
    }

    private void updateRuntime(int runtime) {
        this.runtime = runtime;
    }

    private void updateAgeRating(String ageRating) {
        this.ageRating = ageRating;
    }

    private void updateReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    private void updateSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    private void updateMovieUrl(String movieUrl) {
        this.movieUrl = movieUrl;
    }

    //=== 수정 메서드 ===//
    public void updateMovie(UpdateMovieRequest request, ActorRepository actorRepository) {
        if(request.getTitle() != null) {
            updateTitle(request.getTitle());
        }
        if(request.getRuntime() != null) {
            updateRuntime(request.getRuntime());
        }
        if(request.getAgeRating() != null) {
            updateAgeRating(request.getAgeRating());
        }
        if(request.getReleaseDate() != null) {
            updateReleaseDate(request.getReleaseDate());
        }
        if(request.getSynopsis() != null) {
            updateSynopsis(request.getSynopsis());
        }
        if(request.getMovieUrl() != null) {
            updateMovieUrl(request.getMovieUrl());
        }
    }

    public void updateMovieActors(List<UpdateMovieActorRequest> movieActorRequests, ActorRepository actorRepository) {
        //기존 영화의 배우 리스트 가져오기
        List<MovieActor> existingActors = this.getMovieActors();

        //요청에서 받은 배우 ID 리스트
        List<Long> newActorIds = movieActorRequests.stream()
                .map(UpdateMovieActorRequest::getActorId)
                .collect(Collectors.toList());

        //추가할 배우 찾기 (기존에 없던 배우 추가)
        List<MovieActor> actorsToAdd = movieActorRequests.stream()
                .filter(request -> existingActors.stream()
                        .noneMatch(movieActor -> movieActor.getActor().getId().equals(request.getActorId()))
                )
                .map(request -> {
                    Actor actor = actorRepository.findById(request.getActorId())
                            .orElseThrow(() -> new ActorNotFoundException("배우를 찾을 수 없습니다: " + request.getActorId()));
                    return MovieActor.createCasting(this, actor, request.getActorRole());
                })
                .collect(Collectors.toList());

        //삭제할 배우 찾기 (새로운 목록에 없는 배우 제거)
        List<MovieActor> actorsToRemove = existingActors.stream()
                .filter(movieActor -> !newActorIds.contains(movieActor.getActor().getId()))
                .collect(Collectors.toList());

        //기존 목록에서 삭제할 배우 제거 & 추가할 배우 추가
        existingActors.removeAll(actorsToRemove);
        existingActors.addAll(actorsToAdd);
    }

}
