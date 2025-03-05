package toyproject.onfilm.director.dto;

import lombok.Getter;

@Getter
public class CreateDirectorRequest {
    private String name;
    private Integer age;
    private String sns;

    public CreateDirectorRequest(String name, Integer age, String sns) {
        this.name = name;
        this.age = age;
        this.sns = sns;
    }
}
