package toyproject.onfilm.domain.actor;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.domain.BaseProfileEntity;

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
        String name = "토니 스타크";
        int age = 40;
        int height = 180;
        int weight = 70;
        String sns = "www.instagram.com/hello";

        Actor actor = actorRepository.save(Actor.builder()
                .name(name)
                .age(age)
                .height(height)
                .weight(weight)
                .sns(sns)
                .build());

        log.info("actor.name = {}", actor.getName());

        //when
        Actor findActor = actorRepository.findAll().get(0);

        assertThat(findActor.getName()).isEqualTo(actor.getName());
    }

    @Transactional
    @Test
    void actorTest_NotNULL() {
        //given
        int age = 40;
        int height = 180;
        int weight = 70;
        String sns = "www.instagram.com/hello";

        Actor actor = actorRepository.save(Actor.builder()
                .age(age)
                .height(height)
                .weight(weight)
                .sns(sns)
                .build());

        //when & then
        assertThatThrownBy(() -> actorRepository.findAll().get(0))
                .isInstanceOf(DataIntegrityViolationException.class);


    }
}
