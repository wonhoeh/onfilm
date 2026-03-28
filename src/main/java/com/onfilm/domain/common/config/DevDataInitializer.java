package com.onfilm.domain.common.config;

import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("test@test.com")) {
            return;
        }

        // data.sql로 생성된 person(id=1)에 연결
        var person = em.find(com.onfilm.domain.movie.entity.Person.class, 1L);
        if (person == null) {
            log.warn("[DevDataInitializer] person id=1 not found. 스킵합니다.");
            return;
        }

        User user = User.create("test@test.com", passwordEncoder.encode("test1234"), "testactor");
        user.attachPerson(person);
        userRepository.save(user);

        log.info("[DevDataInitializer] 테스트 계정 생성 완료: test@test.com / test1234");
    }
}
