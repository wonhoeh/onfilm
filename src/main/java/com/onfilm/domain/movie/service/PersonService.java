package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.CreatePersonSnsRequest;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long initializePersonProfile(CreatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        List<Person.SnsRegistration> snsList = toSnsRegistrations(request.snsList());

        Person person = user.getPerson();
        if (person == null) {
            throw new IllegalStateException("USER_PERSON_REQUIRED");
        }

        person.changeBasicInfo(
                request.name(),
                request.birthDate(),
                request.birthPlace(),
                request.oneLineIntro(),
                request.profileImageKey()
        );
        person.replaceSns(snsList);
        person.replaceProfileTags(request.rawTags());

        return person.getId();
    }

    @Transactional
    public void updatePerson(String publicId, UpdatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        // 내 Person만 수정할 수 있다.
        if (user.getPerson() == null || !Objects.equals(user.getPerson().getPublicId(), publicId)) {
            throw new IllegalStateException("FORBIDDEN");
        }

        person.changeBasicInfo(
                request.name(),
                request.birthDate(),
                request.birthPlace(),
                request.oneLineIntro(),
                request.profileImageKey()
        );

        person.replaceSns(toSnsRegistrations(request.snsList()));

        person.replaceProfileTags(request.rawTags());
    }

    private List<Person.SnsRegistration> toSnsRegistrations(
            List<CreatePersonSnsRequest> requests
    ) {
        return requests.stream()
                .map(request -> new Person.SnsRegistration(
                        request.type(),
                        request.url()
                ))
                .toList();
    }
}
