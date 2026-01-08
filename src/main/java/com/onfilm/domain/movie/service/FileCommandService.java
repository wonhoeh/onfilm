package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileCommandService {

    private final PersonRepository personRepository;
    private final MovieRepository movieRepository;

    @Transactional
    public void updatePersonProfileImage(Long personId, String key) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.changeProfileImageUrl(key);
    }

    @Transactional
    public void addPersonGalleryImage(Long personId, String key) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.addGalleryImageKey(key);
    }

    @Transactional
    public void updateMovieThumbnail(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        movie.changeThumbnailUrl(key);
    }

    @Transactional
    public void updateMovieFile(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        movie.changeMovieUrl(key);
    }

    @Transactional
    public void addMovieTrailer(Long movieId, String key) {
        Movie movie = movieRepository.findById(movieId).orElseThrow();
        movie.addTrailer(key); // Trailer 엔티티 생성됨
    }
}
