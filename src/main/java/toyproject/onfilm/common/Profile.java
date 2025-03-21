package toyproject.onfilm.common;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

//JPA 스펙상 엔티티나 임베디드 타입(@Embeddable)은 자바 기본 생성자를 public 이나 protected로 설정해야함
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
@Getter
public class Profile {

    @Column(nullable = false)
    private String name;
    private Integer age;
    private String sns;

    @Builder
    public Profile(String name, Integer age, String sns) {
        this.name = name;
        this.age = age;
        this.sns = sns;
    }
}
