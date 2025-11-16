package toyproject.onfilm.moviedirector.dto;

import lombok.Getter;
import toyproject.onfilm.moviedirector.entity.MovieDirector;

@Getter
public class MovieDirectorNameResponse {
    private String name;

    public MovieDirectorNameResponse(MovieDirector movieDirector) {
        this.name = movieDirector.getDirector().getName();
    }
}
