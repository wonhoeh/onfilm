package toyproject.onfilm.actor.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.common.Profile;
import toyproject.onfilm.movieactor.entity.MovieActor;

import java.util.List;


/**
 * Actor 엔티티
 * 배우의 정보
 * - 이름
 * - 나이 nullable = true
 * - 참여한 작품(필모그래피)
 * - 신체(키, 몸무게) nullable = true
 * - SNS nullable = true
 * - Entity, Entity Repository 함께 위치 시키기 위해 패키지별로 나눔
 * test
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//@SuperBuilder
@Entity
public class Actor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "actor_id")
    private Long id;

    @Embedded
    private Profile profile;

    //=== 연관 관계 ===
    //배우가 출연한 모든 영화와의 관계
    @OneToMany(mappedBy = "actor", cascade = CascadeType.ALL)
    private List<MovieActor> filmography;

    @Builder
    public Actor(Profile profile) {
        this.profile = profile;
    }
}
