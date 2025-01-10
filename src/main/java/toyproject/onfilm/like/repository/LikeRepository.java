package toyproject.onfilm.like.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.like.entity.Like;

public interface LikeRepository extends JpaRepository<Like, Long> {
}
