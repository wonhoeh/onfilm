package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonService {

    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createPerson(CreatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();
        log.info("userId = {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // rawSns -> PersonSns 변환
        List<PersonSns> snsList = Optional.ofNullable(request.getSnsList())
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
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
    public void updatePerson(String publicId, UpdatePersonRequest request) {
        Long userId = SecurityUtil.currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        // ✅ 권한 체크: 내 Person만 수정 (publicId 기준)
        if (user.getPerson() == null || !Objects.equals(user.getPerson().getPublicId(), publicId)) {
            // 보통 403 매핑 추천 (AccessDeniedException 쓰면 더 깔끔)
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

        // ✅ SNS 전체 교체 (null-safe)
        List<PersonSns> snsEntities = Optional.ofNullable(request.getSnsList())
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .map(r -> PersonSns.builder()
                        .type(r.getType())
                        .url(r.getUrl())
                        .build())
                .toList();
        person.replaceSns(snsEntities);

        // ✅ TAG 전체 교체 (null-safe로 넘기는 게 안전)
        person.replaceProfileTags(
                Optional.ofNullable(request.getRawTags()).orElseGet(List::of)
        );
    }
}
