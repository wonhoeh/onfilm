package toyproject.onfilm.movieactor.dto;

import lombok.Builder;
import lombok.Getter;
import toyproject.onfilm.movieactor.entity.MovieActor;

@Getter
public class MovieActorDto {

    private String name;
    private int age;
    private String sns;
    private String actorsRole;

    @Builder
    public MovieActorDto(String name, int age, String sns, String actorsRole) {
        this.name = name;
        this.age = age;
        this.sns = sns;
        this.actorsRole = actorsRole;
    }
}
