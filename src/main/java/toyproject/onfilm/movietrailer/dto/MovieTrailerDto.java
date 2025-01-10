package toyproject.onfilm.movietrailer.dto;

import lombok.Getter;

@Getter
public class MovieTrailerDto {
    private String trailerUrl;
    private String thumbnailUrl;

    public MovieTrailerDto(String trailerUrl, String thumbnailUrl) {
        this.trailerUrl = trailerUrl;
        this.thumbnailUrl = thumbnailUrl;
    }
}
