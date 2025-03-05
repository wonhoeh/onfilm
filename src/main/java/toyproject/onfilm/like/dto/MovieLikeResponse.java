package toyproject.onfilm.like.dto;

import lombok.Getter;

@Getter
public class MovieLikeResponse {
    private Long movieLikeCount;

    public MovieLikeResponse(Long movieLikeCount) {
        this.movieLikeCount = movieLikeCount;
    }
}
