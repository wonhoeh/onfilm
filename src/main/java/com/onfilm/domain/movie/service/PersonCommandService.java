package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.CreatePersonSnsRequest;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
import com.onfilm.domain.movie.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonCommandService {

    private final CurrentPersonProvider currentPersonProvider;

    public Long initializeProfile(CreatePersonRequest request) {
        Person person = currentPersonProvider.getRequired();
        applyProfile(person, request.name(), request.birthDate(), request.birthPlace(),
                request.oneLineIntro(), request.profileImageKey(), request.snsList(), request.rawTags());
        return person.getId();
    }

    public void updateProfile(String publicId, UpdatePersonRequest request) {
        Person person = currentPersonProvider.getRequired(publicId);
        applyProfile(person, request.name(), request.birthDate(), request.birthPlace(),
                request.oneLineIntro(), request.profileImageKey(), request.snsList(), request.rawTags());
    }

    private void applyProfile(
            Person person,
            String name,
            java.time.LocalDate birthDate,
            String birthPlace,
            String oneLineIntro,
            String profileImageKey,
            List<CreatePersonSnsRequest> snsRequests,
            List<String> rawTags
    ) {
        person.changeBasicInfo(name, birthDate, birthPlace, oneLineIntro, profileImageKey);
        person.replaceSns(toSnsRegistrations(snsRequests));
        person.replaceProfileTags(rawTags);
    }

    private List<Person.SnsRegistration> toSnsRegistrations(List<CreatePersonSnsRequest> requests) {
        return requests.stream()
                .map(request -> new Person.SnsRegistration(request.type(), request.url()))
                .toList();
    }
}
