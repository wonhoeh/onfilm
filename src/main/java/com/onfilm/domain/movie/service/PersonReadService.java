package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.event.StorageFilesDeleteEvent;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonReadService {

    private final PersonRepository personRepository;
    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final StorageKeyPolicy storageKeyPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public ProfileResponse findProfileByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        String key = person.getProfileImageKey();
        String publicUrl = (key == null || key.isBlank()) ? null : storageService.toPublicUrl(key);

        return ProfileResponse.from(person, publicUrl);
    }

    public Long findCurrentPersonId() {
        String principal = SecurityUtil.currentPrincipal();

        Long userId;
        try {
            userId = Long.valueOf(principal);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("INVALID_PRINCIPAL");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND"));

        if (user.getPerson() == null) {
            throw new IllegalStateException("PERSON_NOT_LINKED");
        }

        return user.getPerson().getId();
    }

    public String findProfileImageKey(Long personId) {
        return personRepository.findProfileImageKeyById(personId)
                .orElse(null);
    }

    public Long findPersonIdByPublicId(String publicId) {
        return personRepository.findByPublicId(publicId)
                .map(Person::getId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
    }

    public String findFilmographyKey(Long personId) {
        return personRepository.findFilmographyKeyById(personId)
                .orElse(null);
    }

    public String findFilmographyPublicUrlByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        String key = person.getFilmographyFileKey();
        return (key == null || key.isBlank()) ? null : storageService.toPublicUrl(key);
    }

    public boolean isFilmographyPrivate(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return person.isFilmographyPrivate();
    }

    public boolean isGalleryPrivate(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return person.isGalleryPrivate();
    }

    public List<Person.GalleryItem> findGalleryItemsByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return new ArrayList<>(person.getGalleryItems());
    }

    // =============================
    // WRITE — 현재 유저 소유 데이터만 수정
    // =============================

    @Transactional
    public void updatePersonProfileImage(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeProfileImageKey(key);
    }

    @Transactional
    public void addPersonGalleryImage(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.addGalleryImageKey(key);
    }

    @Transactional
    public void updateMovieThumbnail(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        movie.changeThumbnailUrl(key);
    }

    @Transactional
    public void updateMovieFile(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        movie.changeMovieUrl(key);
    }

    @Transactional
    public void addMovieTrailer(Long movieId, String storageKey) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        storageKeyPolicy.validateMovieTrailerKey(movieId, storageKey);
        movie.addTrailer(storageKey);
    }

    public boolean canEditMovie(Long personId, Long movieId) {
        return moviePersonRepository.findByPersonIdAndMovieId(personId, movieId) != null;
    }

    @Transactional
    public void updateFilmographyFile(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeFilmographyFileKey(key);
    }

    @Transactional
    public void clearProfileImage() {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeProfileImageKey(null);
    }

    @Transactional
    public void deleteMovieFiles(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));

        String thumbnailKey = movie.getThumbnailUrl();
        String movieKey = movie.getMovieUrl();

        List<String> trailerKeys = movie.getTrailers().stream()
                .map(Trailer::getStorageKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        trailerKeys.forEach(
                key -> storageKeyPolicy.validateMovieTrailerKey(movieId, key)
        );

        movie.clearThumbnailUrl();
        movie.clearMovieUrl();
        movie.clearTrailers();

        List<String> keys = new ArrayList<>();
        keys.add(thumbnailKey);
        keys.add(movieKey);
        keys.addAll(trailerKeys);
        publishFilesDeleteEvent(keys);
    }

    @Transactional
    public void deleteMovieThumbnail(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        String key = movie.getThumbnailUrl();
        movie.clearThumbnailUrl();
        publishFilesDeleteEvent(Collections.singletonList(key));
    }

    @Transactional
    public void deleteMovieFile(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        String key = movie.getMovieUrl();
        movie.clearMovieUrl();
        publishFilesDeleteEvent(Collections.singletonList(key));
    }

    @Transactional
    public void deleteMovieTrailers(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        List<String> keys = movie.getTrailers().stream()
                .map(Trailer::getStorageKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        keys.forEach(key -> storageKeyPolicy.validateMovieTrailerKey(movieId, key));
        movie.clearTrailers();
        publishFilesDeleteEvent(keys);
    }

    @Transactional
    public void removeGalleryImage(String key) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.removeGalleryImageKey(key);
    }

    @Transactional
    public void reorderGallery(java.util.List<String> orderedKeys) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.reorderGallery(orderedKeys);
    }

    @Transactional
    public void updateFilmographyPrivate(boolean isPrivate) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeFilmographyPrivate(isPrivate);
    }

    @Transactional
    public void updateGalleryPrivate(boolean isPrivate) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeGalleryPrivate(isPrivate);
    }

    @Transactional
    public void updateGalleryItemPrivacy(String key, boolean isPrivate) {
        Long personId = findCurrentPersonId();
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeGalleryItemPrivacy(key, isPrivate);
    }

    private void publishFilesDeleteEvent(List<String> keys) {
        List<String> keysToDelete = keys.stream()
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        if (!keysToDelete.isEmpty()) {
            eventPublisher.publishEvent(new StorageFilesDeleteEvent(keysToDelete));
        }
    }

}
