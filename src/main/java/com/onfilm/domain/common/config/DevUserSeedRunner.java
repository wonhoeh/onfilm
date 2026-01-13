package com.onfilm.domain.common.config;

import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevUserSeedRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // TEMP_USER_SEED_START: delete this block when no longer needed.
        final String email = "test@gmail.com";
        final String password = "qwer1234";
        final String username = "testuser";

        if (userRepository.existsByEmail(email)) return;

        String hashed = passwordEncoder.encode(password);
        User user = User.create(email, hashed, username);
        Person person = Person.create(username, null, null, null, null, null, null);
        user.attachPerson(person);
        userRepository.save(user);
        // TEMP_USER_SEED_END
    }
}
