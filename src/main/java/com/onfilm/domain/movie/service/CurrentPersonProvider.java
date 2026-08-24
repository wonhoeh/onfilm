package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.AuthenticationRequiredException;
import com.onfilm.domain.common.error.exception.ForbiddenPersonAccessException;
import com.onfilm.domain.common.error.exception.PersonNotLinkedException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CurrentPersonProvider {

    private final UserRepository userRepository;

    public Person getRequired() {
        Long userId = SecurityUtil.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Person person = user.getPerson();
        if (person == null) {
            throw new PersonNotLinkedException();
        }
        return person;
    }

    public Person getRequired(String publicId) {
        Person person = getRequired();
        if (!Objects.equals(person.getPublicId(), publicId)) {
            throw new ForbiddenPersonAccessException();
        }
        return person;
    }

    public Long getRequiredId() {
        return getRequired().getId();
    }

    public boolean isCurrentPerson(Long personId) {
        try {
            return Objects.equals(getRequiredId(), personId);
        } catch (AuthenticationRequiredException | UserNotFoundException | PersonNotLinkedException exception) {
            return false;
        }
    }
}
