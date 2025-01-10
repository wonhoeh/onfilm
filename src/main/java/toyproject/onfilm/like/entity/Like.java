package toyproject.onfilm.like.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.domain.user.User;
import toyproject.onfilm.movie.entity.Movie;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "Likes")
public class Like {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="like_id")
    private Long id;

    //=== 연관 관계 ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    //=== 메서드 ===
    public int likes(Movie movie) {
        int totalLike = 0;
        //매개변수로 입력받은 영화의 좋아요 수를 구한다
        //영화의 id값의 갯수가 좋아요 갯수임
        return totalLike;
    }

}
