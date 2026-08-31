package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_movie_person_movie_id_person_id",
                columnNames = {"movie_id", "person_id"}
        )
)
public class MoviePerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @OneToMany(mappedBy = "moviePerson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 100)
    private List<MoviePersonRole> roles = new ArrayList<>();

    // 해당 인물의 필모그래피 표시 순서
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 해당 인물 프로필에서 참여 작품 공개 여부
    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    private MoviePerson(Person person, List<RoleRegistration> roleRegistrations) {
        this.person = require(person, "person");
        replaceRoles(roleRegistrations);
    }

    static MoviePerson create(Person person, List<RoleRegistration> roleRegistrations) {
        return new MoviePerson(person, roleRegistrations);
    }

    void attachMovie(Movie movie) {
        Movie requiredMovie = require(movie, "movie");
        if (this.movie != null && this.movie != requiredMovie) {
            throw new IllegalStateException("moviePerson already belongs to another movie");
        }
        this.movie = requiredMovie;
    }

    void detachMovie(Movie movie) {
        if (this.movie == movie) {
            this.movie = null;
        }
    }

    public MoviePersonRole addRole(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        MoviePersonRole moviePersonRole = MoviePersonRole.create(role, castType, characterName);
        addRole(moviePersonRole);
        return moviePersonRole;
    }

    void addRole(MoviePersonRole moviePersonRole) {
        MoviePersonRole requiredRole = require(moviePersonRole, "moviePersonRole");
        if (hasRole(requiredRole.getRole())) {
            throw new IllegalArgumentException("duplicate movie person role");
        }

        requiredRole.attachMoviePerson(this);
        roles.add(requiredRole);
    }

    public void removeRole(MoviePersonRole moviePersonRole) {
        MoviePersonRole requiredRole = require(moviePersonRole, "moviePersonRole");
        if (!roles.contains(requiredRole)) {
            throw new IllegalArgumentException("role does not belong to moviePerson");
        }
        if (roles.size() == 1) {
            throw new IllegalStateException("moviePerson must have at least one role");
        }

        roles.remove(requiredRole);
        requiredRole.detachMoviePerson(this);
    }

    public void replaceRoles(List<RoleRegistration> roleRegistrations) {
        List<MoviePersonRole> replacements = createRoleReplacements(roleRegistrations);
        Map<PersonRole, MoviePersonRole> existingByRole = new EnumMap<>(PersonRole.class);
        for (MoviePersonRole existing : roles) {
            existingByRole.put(existing.getRole(), existing);
        }

        List<MoviePersonRole> reordered = new ArrayList<>(replacements.size());
        for (MoviePersonRole replacement : replacements) {
            MoviePersonRole existing = existingByRole.remove(replacement.getRole());
            if (existing != null) {
                existing.changeDetails(replacement.getCastType(), replacement.getCharacterName());
                reordered.add(existing);
                continue;
            }

            replacement.attachMoviePerson(this);
            reordered.add(replacement);
        }

        existingByRole.values().forEach(existing -> existing.detachMoviePerson(this));
        roles.clear();
        roles.addAll(reordered);
    }

    public boolean hasRole(PersonRole role) {
        PersonRole requiredRole = require(role, "role");
        return roles.stream().anyMatch(existing -> existing.getRole() == requiredRole);
    }

    public void changeSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be zero or greater");
        }
        this.sortOrder = sortOrder;
    }

    public void changePrivacy(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public List<MoviePersonRole> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    private static List<MoviePersonRole> createRoleReplacements(
            List<RoleRegistration> roleRegistrations
    ) {
        List<RoleRegistration> requiredRegistrations = require(roleRegistrations, "roles");
        if (requiredRegistrations.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }

        Map<PersonRole, MoviePersonRole> uniqueRoles = new LinkedHashMap<>();
        for (RoleRegistration registration : requiredRegistrations) {
            RoleRegistration requiredRegistration = require(registration, "roleRegistration");
            MoviePersonRole role = MoviePersonRole.create(
                    requiredRegistration.role(),
                    requiredRegistration.castType(),
                    requiredRegistration.characterName()
            );
            if (uniqueRoles.putIfAbsent(role.getRole(), role) != null) {
                throw new IllegalArgumentException("duplicate movie person role");
            }
        }
        return List.copyOf(uniqueRoles.values());
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    public record RoleRegistration(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
    }
}
