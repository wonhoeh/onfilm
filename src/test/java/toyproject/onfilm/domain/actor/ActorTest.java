package toyproject.onfilm.domain.actor;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.actor.repository.ActorRepository;
import toyproject.onfilm.common.Profile;


import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Slf4j
public class ActorTest {

    @Autowired
    ActorRepository actorRepository;


    @Transactional
    @Test
    void actorTest_모든컬럼저장() {
        //given
        Profile tonyProfile = Profile.builder()
                .name("토니 스타크")
                .age(40)
                .sns("www.instagram.com/mark404")
                .build();

        Actor tony = actorRepository.save(Actor.builder()
                .profile(tonyProfile)
                .build());

        log.info("actor.name = {}", tony.getProfile().getName());

        //when
        Actor findActor = actorRepository.findAll().get(0);

        assertThat(findActor.getProfile().getName()).isEqualTo(tony.getProfile().getName());
    }

    @Test
    void createActor() {
        //given
        Profile profile = Profile.builder()
                .name("허원회")
                .age(33)
                .sns("www.instagram.com/hi")
                .build();

        Actor actor = actorRepository.save(Actor.builder()
                .profile(profile)
                .build());

        Actor findActor = actorRepository.findByProfileName(actor.getProfile().getName()).get();

        assertThat(findActor.getId()).isEqualTo(actor.getId());
    }
}
