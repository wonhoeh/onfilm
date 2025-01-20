package toyproject.onfilm.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;
import toyproject.onfilm.movieactor.dto.MovieActorRequest;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class CreateMovieRequest {
    @NotBlank
    private String title;
    @NotBlank
    private int runtime;
    @NotBlank
    private String ageRating;
    @NotBlank
    private LocalDateTime releaseDate;
    @Length(min= 1, max=200)
    private String synopsis;
    @NotBlank
    private List<MovieActorRequest> actors; //출연 배우 정보
    @NotBlank
    private String movieFile;
    @NotBlank
    private String thumbnailFile;
    @NotBlank
    private String trailerFile;

    public CreateMovieRequest(String title, int runtime, String ageRating, LocalDateTime releaseDate, String synopsis, List<MovieActorRequest> actors, String movieFile, String thumbnailFile, String trailerFile) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.synopsis = synopsis;
        this.actors = actors;
        this.movieFile = movieFile;
        this.thumbnailFile = thumbnailFile;
        this.trailerFile = trailerFile;
    }
}
