package toyproject.onfilm.domain.actor;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.domain.BaseProfileEntity;
import toyproject.onfilm.domain.Profile;

import java.util.ArrayList;
import java.util.List;

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
}
