package com.onfilm.domain.movie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;
import java.util.List;

@Getter
public class UpdateMovieRequest {
    private String title;
    private Integer runtime;
    private String ageRating;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDate releaseDate;
    @Length(min = 1, max = 200, message = "시놉시스는 1~200자 사이여야 합니다.")
    private String synopsis;
    private String movieUrl;
    private List<UpdateMovieActorRequest> movieActors;  // 배우ID, 역할
    private List<Long> directorIds;   // 감독 ID 리스트
    private List<Long> writerIds;     // 작가 ID 리스트
    private List<String> genreIds;  // 장르 ID 리스트 (MongoDB에서 가져옴)

    public UpdateMovieRequest(String title, int runtime, String ageRating,
                              LocalDate releaseDate, String synopsis, String movieUrl,
                              List<UpdateMovieActorRequest> movieActors,
                              List<Long> directorIds,
                              List<Long> writerIds,
                              List<String> genreIds) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.movieUrl = movieUrl;
        this.movieActors = movieActors;
        this.directorIds = directorIds;
        this.writerIds = writerIds;
        this.genreIds = genreIds;
    }
}
