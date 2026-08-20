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
import java.util.Optional;

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

        List<Person.SnsRegistration> snsList = toSnsRegistrations(request.getSnsList());

        Person person = user.getPerson();
        if (person == null) {
            throw new IllegalStateException("USER_PERSON_REQUIRED");
        }

        person.changeBasicInfo(
                request.getName(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getOneLineIntro(),
                request.getProfileImageUrl()
        );
        person.replaceSns(snsList);
        person.replaceProfileTags(
                request.getRawTags() == null ? List.of() : request.getRawTags()
        );

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
        String imageValue = request.getProfileImageKey();
        if (imageValue == null || imageValue.isBlank()) {
            imageValue = request.getProfileImageUrl();
        }

        person.changeBasicInfo(
                request.getName(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getOneLineIntro(),
                imageValue
        );

        person.replaceSns(toSnsRegistrations(request.getSnsList()));

        // ✅ TAG 전체 교체 (null-safe로 넘기는 게 안전)
        person.replaceProfileTags(
                Optional.ofNullable(request.getRawTags()).orElseGet(List::of)
        );
    }

    private List<Person.SnsRegistration> toSnsRegistrations(
            List<CreatePersonSnsRequest> requests
    ) {
        return Optional.ofNullable(requests)
                .orElseGet(List::of)
                .stream()
                .map(request -> {
                    if (request == null) {
                        throw new IllegalArgumentException("sns request is required");
                    }
                    return new Person.SnsRegistration(
                            request.getType(),
                            request.getUrl()
                    );
                })
                .toList();
    }
}
