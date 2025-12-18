package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.PersonSnsRequest;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonSns;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonService {

    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;

    @Transactional
    public Long createPerson(CreatePersonRequest request, String profileImageUrl) {
        List<PersonSns> snsList = new ArrayList<>();
        if (request.getSnsList() != null) {
            for (PersonSnsRequest snsRequest : request.getSnsList()) {
                snsList.add(PersonSns.builder()
                        .type(snsRequest.getType())
                        .url(snsRequest.getUrl())
                        .build());
            }
        }

        Person person = Person.create(
                request.getName(),
                request.getBirthDate(),
                null,
                null,
                profileImageUrl,
                snsList,
                new ArrayList<>()
        );

        return personRepository.save(person).getId();
    }

    @Transactional(readOnly = true)
    public List<MoviePerson> getFilmography(Long personId) {
        return moviePersonRepository.findFilmography(personId);
    }
}
