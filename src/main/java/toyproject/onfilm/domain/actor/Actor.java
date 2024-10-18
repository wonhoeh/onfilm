package toyproject.onfilm.domain.actor;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
 * - 학력
 * - Entity 클래스와 기본 Entity Repository 함께 위치 시키기 위해 패키지별로 나눔
 */
@Getter
@NoArgsConstructor
@SuperBuilder
@Entity
public class Actor extends BaseProfileEntity {
    //=== 연관 관계 ===
    @Id @GeneratedValue
    @Column(name = "actor_id")
    private Long id;

    //배우가 출연한 모든 영화와의 관계
    @OneToMany(mappedBy = "actor", cascade = CascadeType.ALL)
    private List<MovieActor> filmography = new ArrayList<>();
}
