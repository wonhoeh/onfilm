package com.onfilm.domain.movie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMovieRequest {
    @NotBlank(message = "제목(title)은 필수 입력 항목입니다.")
    private String title;

    @NotNull(message = "상영 시간(runtime)은 필수 입력 항목입니다.")
    private int runtime;

    @NotBlank(message = "관람 등급(ageRating)은 필수 입력 항목입니다.")
    private String ageRating;

    @NotNull(message = "개봉일(releaseDate)은 필수 입력 항목입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate releaseDate;

    @NotBlank
    @Length(min = 1, max = 200, message = "시놉시스는 1~200자 사이여야 합니다.")
    private String synopsis;

    private List<Long> genreIds;  // 장르 ID 목록 [1, 2, 3]
    private List<String> newGenres;   // 신규 장르 이름들

    @NotEmpty
    private List<CreatePersonRequest> people;

    @NotEmpty
    private List<MoviePersonRequest> moviePeople;
}