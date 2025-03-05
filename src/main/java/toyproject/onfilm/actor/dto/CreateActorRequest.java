package toyproject.onfilm.actor.dto;

import lombok.Getter;

@Getter
public class CreateActorRequest {
    private String name;
    private Integer age;
    private String sns;

    public CreateActorRequest(String name, Integer age, String sns) {
        this.name = name;
        this.age = age;
        this.sns = sns;
    }
}
