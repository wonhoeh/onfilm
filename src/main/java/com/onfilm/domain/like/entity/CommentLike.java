package com.onfilm.domain.like.entity;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Document(collection = "comment_likes")
public class CommentLike {

    @Id
    private String id;
    private String commentId;       //좋아요 누른 댓글의 Id
    private String userId;          //좋아요 누른 유저의 Id(UUID, clientID)
    private LocalDateTime likeAt;   //좋아요 누른 날짜

    //=== 생성 메서드 ===//
    public static CommentLike create(String commentId, String userId) {
        CommentLike commentLike = new CommentLike();
        commentLike.commentId = commentId;
        commentLike.userId = userId;
        commentLike.likeAt = LocalDateTime.now();
        return commentLike;
    }
}
