package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.movie.dto.PersonResponse;
import com.onfilm.domain.movie.dto.ProfileAndPublicIdResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonQueryService {

    private final UserRepository userRepository;

    public ProfileAndPublicIdResponse getProfileByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        Person person = user.getPerson();
        if (person == null) throw new PersonNotFoundException(username);

        PersonResponse profile = PersonResponse.from(person);

        return ProfileAndPublicIdResponse.builder()
                .username(username)
                .publicId(person.getPublicId())
                .personResponse(profile)
                .build();
    }
}
