package toyproject.onfilm.domain.movie;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.movieactor.MovieActor;

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
@NoArgsConstructor
@Entity
public class Movie {

    //감독, 출연, 각본, 장르, 영화 특징, 관람등급
    @Id @GeneratedValue
    @Column(name = "movie_id")
    private Long id;

    //영화에 출연한 배우들
    @OneToMany(mappedBy = "movie")
    private List<MovieActor> movieActors = new ArrayList<>();

    private String title;
    private String genre;

    //상영일
    private LocalDate releaseDate;

    //종료일
    private LocalDate closeDate;


    //=== 생성자 ===
    @Builder
    public Movie(String title, String genre, LocalDate releaseDate, LocalDate closeDate) {
        this.title = title;
        this.genre = genre;
        this.releaseDate = releaseDate;
        this.closeDate = closeDate;
    }


}
