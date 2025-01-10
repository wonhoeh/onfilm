package toyproject.onfilm.comment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.domain.user.User;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.user.entity.User;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Comment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="comment_id")
    private Long id;
    private String comment;
    private LocalDateTime createAt;


    //=== 연관 관계 ===

    //댓글을 작성한 유저
    /**
     * 한 명의 유저는 여러 개의 댓글을 작성할 수 있다
     * 하나의 댓글은 한 명의 유저에 의해 작성된다
     * 댓글 : 유저 = N : 1
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    //댓글을 작성한 영화
    /**
     * 한편의 영화는 여러 개의 댓글이 있다
     * 한 개의 댓글은 한 개의 영화에 작성된다
     * 댓글 : 영화 = N : 1
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="movie_id")
    private Movie movie;

}
