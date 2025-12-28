package com.onfilm.domain.user.repository;

import com.onfilm.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    @Query("select u from User u join fetch u.person where u.username = :username")
    Optional<User> findByUsernameWithPerson(@Param("username")String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
