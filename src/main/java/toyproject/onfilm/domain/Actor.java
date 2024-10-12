package toyproject.onfilm.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


/**
 * Actor 엔티티
 * 배우의 개인 정보
 * - 이름
 * - 나이
 * - 참여한 작품(필모그래피)
 * - 핸드폰 번호
 * - 이메일 주소
 */
@Entity
@Data
public class Actor {

    //=== 연관 관계 ===
    @Id @GeneratedValue
    @Column(name = "Actor_Id")
    private Long id;

    @OneToMany(mappedBy = "actor")
    private List<Casting> filmography = new ArrayList<>();

    @Embedded
    private Profile profile;

    //기본 생성자
    public Actor() {}

    //생성자
    public Actor(Profile profile) {
        this.profile = profile;
    }
}
