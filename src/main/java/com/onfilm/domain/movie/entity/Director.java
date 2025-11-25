package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Director 엔티티
 * 감독의 개인 정보
 * - 이름
 * - 나이
 * - 참여한 작품(필모그래피)
 * - 핸드폰 번호
 * - 이메일 주소
 * - 전공 분야 (메인감독, 조감독, 촬영감독...)
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Director {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "director_id")
    private Long id;

    @Column(nullable = false)
    private String name;
    private LocalDate birthDate;
    private String sns;

    @OneToMany(mappedBy = "director", cascade = CascadeType.ALL)
    List<MovieDirector> filmography = new ArrayList<>();

    @Builder
    public Director(String name, LocalDate birthDate, String sns) {
        this.name = name;
        this.birthDate = birthDate;
        this.sns = sns;
    }
}
