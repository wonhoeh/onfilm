package toyproject.onfilm.actor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.domain.actor.Actor;

public interface ActorRepository extends JpaRepository<Actor, Long> {
}
