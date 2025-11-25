package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Writer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WriterRepository extends JpaRepository<Writer, Long> {
}
