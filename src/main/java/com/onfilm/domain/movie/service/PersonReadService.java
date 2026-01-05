package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.movie.dto.PersonResponse;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonReadService {

    private final PersonRepository personRepository;

    public PersonResponse getProfileByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        return PersonResponse.from(person);
    }
}
