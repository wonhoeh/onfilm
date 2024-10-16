package toyproject.onfilm.domain.director;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.BaseProfileEntity;
import toyproject.onfilm.domain.moviedirector.MovieDirector;

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
@NoArgsConstructor
@Entity
public class Director {

    @Id @GeneratedValue
    @Column(name = "director_id")
    private Long id;

    @OneToMany(mappedBy = "director")
    List<MovieDirector> filmography = new ArrayList<>();

    @Builder
    public Director(BaseProfileEntity baseProfileEntity) {
        this.baseProfileEntity = baseProfileEntity;
    }

}
