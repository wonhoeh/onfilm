package toyproject.onfilm.writer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.writer.entity.Writer;

public interface WriterRepository extends JpaRepository<Writer, Long> {
}
