package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
