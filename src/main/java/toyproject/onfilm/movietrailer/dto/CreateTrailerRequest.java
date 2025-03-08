package toyproject.onfilm.movietrailer.dto;

import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
public class CreateTrailerRequest {

    private Long movieId;              //영화 Id
    private MultipartFile trailer;     //예고편 파일
    private MultipartFile thumbnail;   //섬네일 파일

    public CreateTrailerRequest(Long movieId, MultipartFile trailer, MultipartFile thumbnail) {
        this.movieId = movieId;
        this.trailer = trailer;
        this.thumbnail = thumbnail;
    }
}
