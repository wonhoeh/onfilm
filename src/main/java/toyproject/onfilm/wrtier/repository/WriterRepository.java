package toyproject.onfilm.wrtier.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.wrtier.entity.Writer;

public interface WriterRepository extends JpaRepository<Writer, Long> {
}
