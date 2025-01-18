package toyproject.onfilm.comment.entity;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Document(collection = "comments")
public class Comment {

    @Id
    private String id;          //MongoDB의 ObjectId
    private Long movieId;     //연관된 영화 ID
    private String username;    //댓글 작성자
    private String content;     //댓글 내용
    private LocalDateTime createAt; //작성 시간

    public Comment(Long movieId, String username, String content) {
        this.movieId = movieId;
        this.username = username;
        this.content = content;
        this.createAt = LocalDateTime.now();    //댓글 작성 시 자동으로 시간 설정
    }
}
