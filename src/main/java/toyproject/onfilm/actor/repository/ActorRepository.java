package toyproject.onfilm.actor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.actor.entity.Actor;

import java.util.List;
import java.util.Optional;

public interface ActorRepository extends JpaRepository<Actor, Long> {
}
