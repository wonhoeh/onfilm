package com.onfilm.domain.like.entity;


import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor
@Document(collection = "movie_likes")
public class MovieLike {
    @Id
    private String id;          //MongoDB의 ObjectId
    private Long movieId;       //좋아요가 눌린 영화 ID
    private String clientId;    //특정 클라이언트를 식별하기 위한 고유 값 예시)UUID

    //=== 생성 메서드 ===//
    public static MovieLike create(Long movieId, String clientId) {
        MovieLike movieLike = new MovieLike();
        movieLike.movieId = movieId;
        movieLike.clientId = clientId;
        return movieLike;
    }
}
