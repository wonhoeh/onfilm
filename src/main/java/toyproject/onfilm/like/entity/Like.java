package toyproject.onfilm.like.entity;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor
@Document(collection = "likes")
public class Like {

    @Id
    private String id;  //MongoDB의 ObjectId
    private Long movieId; //좋아요가 눌린 영화 ID
    private String clientId;//특정 클라이언트를 식별하기 위한 고유 값 예시)UUID

    public Like(Long movieId, String clientId) {
        this.movieId = movieId;
        this.clientId = clientId;
    }
}
