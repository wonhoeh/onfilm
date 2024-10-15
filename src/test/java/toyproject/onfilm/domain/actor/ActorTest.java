package toyproject.onfilm.domain.actor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.domain.Profile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
public class ActorTest {

    @Autowired ActorRepository actorRepository;

    @Test
    @Transactional
    void actorTest() {
        //given
        String name = "토니 스타크";
        int age = 40;
        String phoneNumber = "010-1234-5678";
        String email = "tonyMk2@gmail.com";


        Profile profile = Profile.builder()
                .name(name)
                .age(age)
                .phoneNumber(phoneNumber)
                .email(email)
                .build();

        Actor actor = actorRepository.save(Actor.builder()
                .profile(profile)
                .build());

        //when
        List<Actor> actors = actorRepository.findAll();
        Actor findActor = actors.get(0);

        assertThat(findActor.getProfile().getName()).isEqualTo(actor.getProfile().getName());
    }
}
