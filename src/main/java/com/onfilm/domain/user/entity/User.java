package com.onfilm.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "Users")
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
//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    List<Comment> comments = new ArrayList<>();

    //유저가 누른 좋아요
//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    List<Like> likes = new ArrayList<>();
}
