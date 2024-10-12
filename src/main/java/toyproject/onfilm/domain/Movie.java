package toyproject.onfilm.domain;

import jakarta.persistence.*;
import lombok.Data;

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
 */

@Entity
@Data
public class Movie {

    //감독, 출연, 각본, 장르, 영화 특징, 관람등급
    @Id @GeneratedValue
    @Column(name = "Movie_Id")
    private Long id;

    //영화에 출연한 배우들
    @OneToMany(mappedBy = "actor")
    private List<Casting> actors = new ArrayList<>();

    //영화 제작에 참여한 감독들
    @OneToMany(mappedBy = "director")
    private List<Casting> directors = new ArrayList<>();

    //캐스팅된 영화 작품
    @OneToOne(mappedBy = "movie")
    private Casting casting;


    private String title;
    private String genre;

    //2024-10-20, 상영일
    private LocalDateTime releaseDate;       //2024-10-20

    //종료일
    private LocalDateTime closeDate;


}
