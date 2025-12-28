package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.CreatePersonSnsRequest;
import com.onfilm.domain.movie.dto.PersonResponse;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonService {

    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    @Transactional
    public PersonResponse getPersonByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("USER NOT FOUND"));

        Person person = user.getPerson();
        if (person == null) {
            throw new IllegalArgumentException("PERSON NOT FOUND");
        }

        return PersonResponse.from(person);
    }

    @Transactional
    public Long createPerson(CreatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();
        log.info("userId = {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("USER NOT FOUND"));

        List<PersonSns> snsList = new ArrayList<>();
        if (request.getSnsList() != null) {
            for (CreatePersonSnsRequest snsRequest : request.getSnsList()) {
                snsList.add(PersonSns.builder()
                        .type(snsRequest.getType())
                        .url(snsRequest.getUrl())
                        .build());
            }
        }

        Person person = Person.create(
                request.getName(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getOneLineIntro(),
                request.getProfileImageUrl(),
                snsList,
                request.getRawTags()
        );

        user.assignPerson(person);

        userRepository.save(user);

        return person.getId();
    }

    @Transactional(readOnly = true)
    public List<MoviePerson> getFilmography(Long personId) {
        return moviePersonRepository.findFilmography(personId);
    }
}
