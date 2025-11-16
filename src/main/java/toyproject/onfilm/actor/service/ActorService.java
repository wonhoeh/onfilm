package toyproject.onfilm.actor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.dto.CreateActorRequest;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.actor.repository.ActorRepository;

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
                .age(request.getAge())
                .sns(request.getSns())
                .build());

        return actor.getId();
    }
}
