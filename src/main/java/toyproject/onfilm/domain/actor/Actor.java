package toyproject.onfilm.domain.actor;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.Profile;
import toyproject.onfilm.domain.movieactor.MovieActor;

import java.util.ArrayList;
import java.util.List;


/**
 * Actor 엔티티
 * 배우의 정보
 * - 이름
 * - 나이
 * - 참여한 작품(필모그래피)
 * - 신체(키, 몸무게) nullable
 * - SNS
 * - 학력
 * - Entity 클래스와 기본 Entity Repository 함께 위치 시키기 위해 패키지별로 나눔
 */
@Getter
@NoArgsConstructor
@Entity
public class Actor {

    //=== 연관 관계 ===
    @Id @GeneratedValue
    @Column(name = "actor_id")
    private Long id;

    @OneToMany(mappedBy = "actor")
    private List<MovieActor> filmography = new ArrayList<>();

    @Embedded
    private Profile profile;

    //생성자
    @Builder
    public Actor(Profile profile) {
        this.profile = profile;
    }
}
