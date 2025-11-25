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
        // 1. Person 생성
        Person person = Person.create(request, profileImageUrl);

        // 2. SNS 리스트 추가 (연관간계 설정)
        if (request.getSnsList() != null) {
            for (PersonSnsRequest snsRequest : request.getSnsList()) {
                PersonSns sns = PersonSns.builder()
                        .type(snsRequest.getType())
                        .url(snsRequest.getUrl())
                        .build();
                person.addSns(sns);
            }
        }

        // 3. 저장
        Person saved = personRepository.save(person);

        return saved.getId();
    }

    public List<MoviePerson> getFilmography(Long personId) {
        return moviePersonRepository.findFilmography(personId);
    }
}
