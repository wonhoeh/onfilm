package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.movie.dto.PublicIdByUsernameResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonQueryService {

    private final UserRepository userRepository;

    public PublicIdByUsernameResponse findPublicIdByUsername(String username) {
        Username value = Username.from(username);
        User user = userRepository.findByUsernameNormalized(value.normalized())
                .orElseThrow(() -> new UserNotFoundException(username));

        Person person = user.getPerson();
        if (person == null) throw new PersonNotFoundException(username);

        return new PublicIdByUsernameResponse(user.getUsername(), person.getPublicId());
    }
}
