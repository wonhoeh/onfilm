package com.onfilm.domain.common.config;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.SnsType;
import com.onfilm.domain.movie.entity.StoryboardProject;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private static final String DEV_EMAIL = "test@test.com";
    private static final String DEV_PASSWORD = "test1234";
    private static final String DEV_USERNAME = "testactor";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DEV_EMAIL)) {
            return;
        }

        User user = User.create(
                UserEmail.from(DEV_EMAIL),
                passwordEncoder.encode(DEV_PASSWORD),
                Username.from(DEV_USERNAME)
        );
        Person person = user.createPerson(
                "테스트 배우",
                LocalDate.of(1995, 3, 15),
                "서울",
                "독립영화를 사랑하는 배우입니다.",
                null,
                List.of(
                        new Person.SnsRegistration(
                                SnsType.INSTAGRAM,
                                "https://instagram.com/testactor"
                        ),
                        new Person.SnsRegistration(
                                SnsType.YOUTUBE,
                                "https://youtube.com/testactor"
                        )
                ),
                List.of("연기", "독립영화", "단편영화")
        );
        person.addGalleryImageKey("gallery/1/img1.jpg");
        person.addGalleryImageKey("gallery/1/img2.jpg");
        person.addGalleryImageKey("gallery/1/img3.jpg");

        for (int projectNumber = 1; projectNumber <= 10; projectNumber++) {
            StoryboardProject project = person.addStoryboardProject(
                    "프로젝트 " + projectNumber
            );
            for (int sceneNumber = 1; sceneNumber <= 3; sceneNumber++) {
                project.addScene(
                        "씬 " + sceneNumber,
                        "<p>대사 내용입니다.</p>"
                );
            }
        }

        userRepository.save(user);

        log.info(
                "[DevDataInitializer] 개발 fixture 생성 완료: email={}, projects=10, scenes=30",
                DEV_EMAIL
        );
    }
}
