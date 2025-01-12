package toyproject.onfilm.movietrailer.dto;

import lombok.Getter;
import toyproject.onfilm.movie.dto.MovieDetailsDto;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;

@Getter
public class MovieTrailerResponse {
    private String trailerUrl;
    private String thumbnailUrl;

    public MovieTrailerResponse(MovieTrailer movieTrailer) {
        this.trailerUrl = movieTrailer.getTrailUrl();
        this.thumbnailUrl = movieTrailer.getThumbnailUrl();
    }

    /**
     * DTO로 직접 받을 때 사용
     */
    public MovieTrailerResponse(MovieDetailsDto movieDetailsDto) {
        this.trailerUrl = movieDetailsDto.getTrailerUrl();
        this.thumbnailUrl = movieDetailsDto.getThumbnailUrl();
    }
}
