package toyproject.onfilm.movie.dto;

import lombok.Getter;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;

@Getter
public class MovieThumbnailResponse {
    private String title;
    private String thumbnailUrl;

    public MovieThumbnailResponse(Movie movie) {
        MovieTrailer movieTrailer = movie.getMovieTrailers().isEmpty() ? null : movie.getMovieTrailers().get(0);
        String thumbnailUrl = movieTrailer.getThumbnailUrl();
        this.thumbnailUrl = thumbnailUrl;
        this.title = movie.getTitle();
    }
}
