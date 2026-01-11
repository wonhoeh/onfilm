package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonReadService {

    private final PersonRepository personRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public ProfileResponse findProfileByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        // key -> publicUrl 변환
        String key = person.getProfileImageUrl();
        String publicUrl = (key == null || key.isBlank()) ? null : storageService.toPublicUrl(key);

        return ProfileResponse.from(person, publicUrl);
    }

    public Long findCurrentPersonId() {
        String principal = SecurityUtil.currentPrincipal(); // auth.getName()

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

    public java.util.List<Person.GalleryItem> findGalleryItemsByPublicId(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        return new java.util.ArrayList<>(person.getGalleryItems());
    }

    @Transactional
    public void updatePersonProfileImage(Long personId, String key) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeProfileImageUrl(key);
    }

    @Transactional
    public void addPersonGalleryImage(Long personId, String key) {
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
    public void addMovieTrailer(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        movie.addTrailer(key);
    }

    @Transactional
    public void updateFilmographyFile(Long personId, String key) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeFilmographyFileKey(key);
    }

    @Transactional
    public void clearProfileImage(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeProfileImageUrl(null);
    }

    @Transactional
    public void deleteMovieFiles(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));

        String thumbnailKey = movie.getThumbnailUrl();
        String movieKey = movie.getMovieUrl();

        java.util.List<String> trailerKeys = movie.getTrailers().stream()
                .map(Trailer::getUrl)
                .filter(k -> k != null && !k.isBlank())
                .toList();

        movie.clearThumbnailUrl();
        movie.clearMovieUrl();
        movie.clearTrailers();

        if (thumbnailKey != null && !thumbnailKey.isBlank()) storageService.delete(thumbnailKey);
        if (movieKey != null && !movieKey.isBlank()) storageService.delete(movieKey);
        for (String key : trailerKeys) {
            storageService.delete(key);
        }
    }

    @Transactional
    public void deleteMovieThumbnail(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        String key = movie.getThumbnailUrl();
        movie.clearThumbnailUrl();
        if (key != null && !key.isBlank()) storageService.delete(key);
    }

    @Transactional
    public void deleteMovieFile(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        String key = movie.getMovieUrl();
        movie.clearMovieUrl();
        if (key != null && !key.isBlank()) storageService.delete(key);
    }

    @Transactional
    public void deleteMovieTrailers(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
        java.util.List<String> keys = movie.getTrailers().stream()
                .map(Trailer::getUrl)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        movie.clearTrailers();
        for (String key : keys) {
            storageService.delete(key);
        }
    }

    @Transactional
    public void removeGalleryImage(Long personId, String key) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.removeGalleryImageKey(key);
    }

    @Transactional
    public void reorderGallery(Long personId, java.util.List<String> orderedKeys) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.reorderGallery(orderedKeys);
    }

    @Transactional
    public void updateFilmographyPrivate(Long personId, boolean isPrivate) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeFilmographyPrivate(isPrivate);
    }

    @Transactional
    public void updateGalleryPrivate(Long personId, boolean isPrivate) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.changeGalleryPrivate(isPrivate);
    }

    @Transactional
    public void updateGalleryItemPrivacy(Long personId, String key, boolean isPrivate) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        person.updateGalleryItemPrivacy(key, isPrivate);
    }
}
