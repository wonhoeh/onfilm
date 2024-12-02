package toyproject.onfilm.domain.actor;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import toyproject.onfilm.domain.BaseProfileEntity;
import toyproject.onfilm.domain.movieactor.MovieActor;

import java.util.ArrayList;
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
@SuperBuilder
@Entity
public class Actor extends BaseProfileEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "actor_id")
    private Long id;

    //=== 연관 관계 ===
    //배우가 출연한 모든 영화와의 관계
    @OneToMany(mappedBy = "actor", cascade = CascadeType.ALL)
    private List<MovieActor> filmography;
}
