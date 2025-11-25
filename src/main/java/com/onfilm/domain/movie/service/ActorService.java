package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.CreateActorRequest;
import com.onfilm.domain.movie.entity.Actor;
import com.onfilm.domain.movie.repository.ActorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ActorService {

    private final ActorRepository actorRepository;

    @Transactional
    public Long createActor(CreateActorRequest request) {
        Actor actor = actorRepository.save(Actor.builder()
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .sns(request.getSns())
                .build());

        return actor.getId();
    }
}
