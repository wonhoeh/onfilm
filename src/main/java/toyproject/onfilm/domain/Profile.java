package toyproject.onfilm.domain;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter @Setter
public class Profile {

    private String name;
    private int age;
    private String phoneNumber;
    private String email;

    public Profile() {};

    public Profile(String name, int age) {
        this.name = name;
        this.age = age;
    }

}
