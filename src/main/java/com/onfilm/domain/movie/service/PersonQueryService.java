package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.error.exception.FilmographyFileNotFoundException;
import com.onfilm.domain.movie.dto.PublicIdByUsernameResponse;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonQueryService {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final StorageService storageService;

    public ProfileResponse findProfileByPublicId(String publicId) {
        Person person = findByPublicId(publicId);
        String key = person.getProfileImageKey();
        String publicUrl = key == null || key.isBlank()
                ? null
                : storageService.toPublicUrl(key);
        return ProfileResponse.from(person, publicUrl);
    }

    public String findFilmographyPublicUrlByPublicId(String publicId) {
        String key = findByPublicId(publicId).getFilmographyFileKey();
        if (key == null || key.isBlank()) {
            throw new FilmographyFileNotFoundException(publicId);
        }
        return storageService.toPublicUrl(key);
    }

    public PublicIdByUsernameResponse findPublicIdByUsername(String username) {
        Username value = Username.from(username);
        User user = userRepository.findByUsernameNormalized(value.normalized())
                .orElseThrow(() -> new UserNotFoundException(username));

        Person person = user.getPerson();
        if (person == null) throw new PersonNotFoundException(username);

        return new PublicIdByUsernameResponse(user.getUsername(), person.getPublicId());
    }

    private Person findByPublicId(String publicId) {
        return personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
    }
}
