package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonReadService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    public ProfileResponse getProfileByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        return ProfileResponse.from(person);
    }

    public Long currentPersonId() {
        String principal = SecurityUtil.currentPrincipal(); // auth.getName()

        User user = userRepository.findByEmail(principal)
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND"));

        if (user.getPerson() == null) throw new IllegalStateException("PERSON_NOT_LINKED");
        return user.getPerson().getId();
    }
}
