package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Actor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "actor_id")
    private Long id;

    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private LocalDate birthDate;


    // === 연관 관계 ===
    // 배우가 출연한 모든 영화와의 관계
    @OneToMany(mappedBy = "actor")
    private List<MovieActor> filmography;

    private String sns;

    @Builder
    public Actor(String name, LocalDate birthDate, String sns) {
        this.name = name;
        this.birthDate = birthDate;
        this.sns = sns;
    }
}
