package com.onfilm.domain.movie.entity;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.global.error.exception.ActorNotFoundException;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.dto.UpdateMovieActorRequest;
import com.onfilm.domain.movie.dto.UpdateMovieRequest;
import com.onfilm.domain.movie.repository.ActorRepository;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id", nullable = false)
    private Long id;
    private String title;
    private int runtime;
    private String ageRating;
    private LocalDate releaseDate;
    private String synopsis;
    private String movieUrl;
    private String thumbnailUrl;

//    //영화에 출연한 배우들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)   //한 번에 100개의 MovieActor를 로드
    private List<MovieActor> movieActors = new ArrayList<>();
//
//    //영화 제작에 참여한 감독들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieDirector> movieDirectors = new ArrayList<>();
//
//    //시나리오 집필한 작가들
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieWriter> movieWriters = new ArrayList<>();

    // 출연진, 감독, 작가
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MoviePerson> moviePeople = new ArrayList<>();

    //예고편, 섬네일
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<MovieTrailer> movieTrailers = new ArrayList<>();

    //장르
    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieGenre> genres = new ArrayList<>();

    //영화에 달린 댓글
    @ElementCollection
    private List<String> comments = new ArrayList<>();

    //영화의 좋아요
    @ElementCollection
    private List<String> likes = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    public Movie(String title, int runtime, String ageRating,
                 LocalDate releaseDate, String synopsis,
                 String movieUrl, String thumbnailUrl) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.movieUrl = movieUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    // =================================
    // 정적 팩토리
    // =================================

    public static Movie create(CreateMovieRequest request, String thumbnailUrl, String movieUrl) {
        return Movie.builder()
                .title(request.getTitle())
                .runtime(request.getRuntime())
                .ageRating(request.getAgeRating())
                .releaseDate(request.getReleaseDate())
                .synopsis(request.getSynopsis())
                .movieUrl(movieUrl)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    // =================================
    // 연관관계 편의 메서드
    // =================================

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

    public void addMoviePerson(MoviePerson moviePerson) {
        moviePeople.add(moviePerson);
        moviePerson.setMovie(this);
    }

    public void addTrailer(MovieTrailer movieTrailer) {
        movieTrailers.add(movieTrailer);
        movieTrailer.setMovie(this);
    }

    public void addGenre(Genre genre) {
        MovieGenre movieGenre = MovieGenre.builder()
                .movie(this)
                .genre(genre)
                .build();
        this.genres.add(movieGenre);
    }

    public void addMovieUrl(String movieUrl) {
        this.movieUrl = movieUrl;
    }

    // =================================
    // Setter
    // =================================


    public void addComment(String commentId) {
        comments.add(commentId);
    }

    public void addLike(String movieLikeId) {
        likes.add(movieLikeId);
    }

    public void removeLike(String movieLikeId) {
        likes.remove(movieLikeId);
    }


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

    private void updateReleaseDate(LocalDate releaseDate) {
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
