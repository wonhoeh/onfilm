package toyproject.onfilm.movietrailer.dto;

import lombok.Getter;

@Getter
public class CreateTrailerRequest {

    private String trailUrl;    //예고편 url
    private String thumbnailUrl; //섬네일 url

    public CreateTrailerRequest(String trailUrl, String thumbnailUrl) {
        this.trailUrl = trailUrl;
        this.thumbnailUrl = thumbnailUrl;
    }
}
