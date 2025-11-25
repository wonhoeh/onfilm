package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.CreateDirectorRequest;
import com.onfilm.domain.movie.entity.Director;
import com.onfilm.domain.movie.repository.DirectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DirectorService {

    private final DirectorRepository directorRepository;

    @Transactional
    public Long createDirector(CreateDirectorRequest request) {
        Director director = Director.builder()
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .sns(request.getSns())
                .build();

        Director savedDirector = directorRepository.save(director);

        return savedDirector.getId();
    }
}
