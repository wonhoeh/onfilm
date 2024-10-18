package toyproject.onfilm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
public abstract class BaseProfileEntity  {
    @Column(nullable = false)
    private String name;
    private Integer age;
    private Integer height;
    private Integer weight;
    private String sns;
}
