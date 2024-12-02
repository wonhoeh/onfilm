package toyproject.onfilm.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.comment.Comment;
import toyproject.onfilm.domain.like.Like;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="user_id")
    private Long id;

    private String webId;
    private String password;
    private String name;
    private int age;
    private String email;

    //=== 연관 관계 ===
    //유저가 작성한 댓글 목록
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    List<Comment> comments = new ArrayList<>();

    //유저가 누른 좋아요
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    List<Like> likes = new ArrayList<>();
}
