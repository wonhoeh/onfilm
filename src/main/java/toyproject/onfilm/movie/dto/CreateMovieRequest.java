package toyproject.onfilm.movie.dto;

import lombok.Getter;
import toyproject.onfilm.movieactor.dto.MovieActorRequest;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class CreateMovieRequest {
    private String title;
    private int runtime;
    private String ageRating;
    private LocalDateTime releaseDate;
    private List<MovieActorRequest> actors; //출연 배우 정보
    private String movieFile;
    private String thumbnailFile;
    private String trailerFile;

    public CreateMovieRequest(String title, int runtime, String ageRating, LocalDateTime releaseDate, List<MovieActorRequest> actors, String movieFile, String thumbnailFile, String trailerFile) {
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.releaseDate = releaseDate;
        this.actors = actors;
        this.movieFile = movieFile;
        this.thumbnailFile = thumbnailFile;
        this.trailerFile = trailerFile;
    }
}
