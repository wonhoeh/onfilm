package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.PersonResponse;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
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

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonService {

    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PersonResponse getPersonByUsername(String username) {
        User user = userRepository.findByUsernameWithPerson(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        Person person = user.getPerson();
        if (person == null) throw new PersonNotFoundException(username);

        return PersonResponse.from(person);
    }

    @Transactional
    public Long createPerson(CreatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();
        log.info("userId = {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // rawSns -> PersonSns 변환
        List<PersonSns> snsList = request.getSnsList().stream()
                .map(s -> PersonSns.create(s.getType(), s.getUrl()))
                .toList();

        Person person = Person.create(
                request.getName(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getOneLineIntro(),
                request.getProfileImageUrl(),
                snsList,
                request.getRawTags() == null ? List.of() : request.getRawTags());

        user.attachPerson(person);

        userRepository.save(user);

        return person.getId();
    }

    @Transactional
    public void updatePerson(Long id, UpdatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Person person = personRepository.findById(id)
                .orElseThrow(() -> new PersonNotFoundException(id));

        // ✅ 권한 체크: 내 Person만 수정
        if (user.getPerson() == null || !Objects.equals(user.getPerson().getId(), id)) {
            // 401이 아니라 보통 403이 맞음 (예외 매핑도 같이 확인 추천)
            throw new IllegalStateException("FORBIDDEN");
        }

        // ✅ 기본 필드 업데이트
        person.updateBasic(
                request.getName(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getOneLineIntro(),
                request.getProfileImageUrl()
        );

        // ✅ SNS 전체 교체
        List<PersonSns> snsEntities = (request.getSnsList() == null) ? List.of()
                : request.getSnsList().stream()
                .map(r -> PersonSns.builder()
                        .type(r.getType())
                        .url(r.getUrl())
                        .build())
                .toList();
        person.replaceSns(snsEntities);

        // ✅ TAG 전체 교체 (핵심: replaceProfileTags에서 중복/flush순서 안전 처리)
        person.replaceProfileTags(request.getRawTags());
    }

    @Transactional(readOnly = true)
    public List<MoviePerson> getFilmography(Long personId) {
        return moviePersonRepository.findFilmography(personId);
    }
}
