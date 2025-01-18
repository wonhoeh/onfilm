package toyproject.onfilm.comment.dto;

import lombok.Getter;

@Getter
public class CommentRequest {

    private Long movieId;
    private String username;
    private String content;

}
