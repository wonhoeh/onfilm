package toyproject.onfilm.domain;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CastingTest {

    @Test
    void test() {

        Actor actor1 = new Actor(new Profile("김배우", 20));

        log.info("actor1.name={}, actor1.age={}", actor1.getProfile().getName(), actor1.getProfile().getAge());
        assertThat(actor1.getProfile().getAge()).isEqualTo(20);
    }
}