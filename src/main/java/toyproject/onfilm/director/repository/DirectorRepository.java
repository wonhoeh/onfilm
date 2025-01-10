package toyproject.onfilm.director.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.director.entity.Director;
import toyproject.onfilm.domain.director.Director;

public interface DirectorRepository extends JpaRepository<Director, Long> {
}
