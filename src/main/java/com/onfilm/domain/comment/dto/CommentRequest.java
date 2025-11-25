package com.onfilm.domain.comment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CommentRequest {
    @NotNull
    private Long movieId;
    private String username;
    private String content;

    public CommentRequest(Long movieId, String username, String content) {
        this.movieId = movieId;
        this.username = username;
        this.content = content;
    }
}
