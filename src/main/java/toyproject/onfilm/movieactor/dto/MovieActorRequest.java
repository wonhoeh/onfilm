package toyproject.onfilm.movieactor.dto;

import lombok.Getter;

@Getter
public class MovieActorRequest {
    private String name;
    private int age;
    private String sns;
    private String actorsRole;

    public MovieActorRequest(String name, int age, String sns, String actorsRole) {
        this.name = name;
        this.age = age;
        this.sns = sns;
        this.actorsRole = actorsRole;
    }
}
