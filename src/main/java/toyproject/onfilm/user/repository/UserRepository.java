package toyproject.onfilm.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.domain.user.User;
import toyproject.onfilm.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
