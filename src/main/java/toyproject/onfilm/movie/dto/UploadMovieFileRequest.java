package toyproject.onfilm.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UploadMovieFileRequest {

    @NotBlank
    private String movieId;  //영화 Id

    @NotNull
    private String thumbnailUrl;  //썸네일 이미지 URL
    @NotNull
    private String trailerUrl;    //예고편 영상 URL
    @NotNull
    private String movieUrl;      //실제 영화 파일 URL

    public UploadMovieFileRequest(String movieId, String thumbnailUrl, String trailerUrl, String movieUrl) {
        this.movieId = movieId;
        this.thumbnailUrl = thumbnailUrl;
        this.trailerUrl = trailerUrl;
        this.movieUrl = movieUrl;
    }
}
