package toyproject.onfilm.movie.dto;

import lombok.Getter;
import toyproject.onfilm.movieactor.dto.MovieActorResponse;
import toyproject.onfilm.movietrailer.dto.MovieTrailerResponse;

import java.util.List;

@Getter
public class MovieDetailResponse {
    private Long id;
    private String title;
    private int runtime;
    private String ageRating;
    private List<MovieTrailerResponse> trailers;
    private List<MovieActorResponse> actors;

    public MovieDetailResponse(Long id, String title, int runtime, String ageRating, List<MovieTrailerResponse> trailers, List<MovieActorResponse> actors) {
        this.id = id;
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.trailers = trailers;
        this.actors = actors;
    }
}
