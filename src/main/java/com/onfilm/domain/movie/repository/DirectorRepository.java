package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Director;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectorRepository extends JpaRepository<Director, Long> {
}
