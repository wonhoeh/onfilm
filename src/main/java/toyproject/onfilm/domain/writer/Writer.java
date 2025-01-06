package toyproject.onfilm.domain.writer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import toyproject.onfilm.domain.BaseProfileEntity;
import toyproject.onfilm.domain.Profile;
import toyproject.onfilm.domain.moviewriter.MovieWriter;

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
//@SuperBuilder
@Entity
public class Writer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "writer_id")
    private Long id;

    @Embedded
    private Profile profile;

    @OneToMany(mappedBy = "writer")
    //@Builder.Default
    private List<MovieWriter> filmography = new ArrayList<>();
}
