package toyproject.onfilm.movie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;
import toyproject.onfilm.movieactor.dto.MovieActorRequest;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class CreateMovieRequest {
    @NotBlank(message = "제목(title)은 필수 입력 항목입니다.")
    private String title;

    @NotNull(message = "상영 시간(runtime)은 필수 입력 항목입니다.")
    private int runtime;

    @NotBlank(message = "관람 등급(ageRating)은 필수 입력 항목입니다.")
    private String ageRating;

    @NotNull(message = "개봉일(releaseDate)은 필수 입력 항목입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime releaseDate;

    @NotBlank
    @Length(min = 1, max = 200, message = "시놉시스는 1~200자 사이여야 합니다.")
    private String synopsis;

    @NotEmpty
    private List<MovieActorRequest> movieActors;  // 배우ID, 역할

    @NotEmpty
    private List<Long> directorIds;   // 감독 ID 리스트

    @NotEmpty
    private List<Long> writerIds;     // 작가 ID 리스트

    @NotEmpty
    private List<String> genreIds;  // 장르 ID 리스트 (MongoDB에서 가져옴)

    public CreateMovieRequest(String title, int runtime, String ageRating, LocalDateTime releaseDate, String synopsis,
                              List<MovieActorRequest> movieActors,
                              List<Long> directorIds,
                              List<Long> writerIds,
                              List<String> genreIds) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.movieActors = movieActors;
        this.directorIds = directorIds;
        this.writerIds = writerIds;
        this.genreIds = genreIds;
    }
}
